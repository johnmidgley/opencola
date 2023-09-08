package io.opencola.network

import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.model.Id
import io.opencola.network.message.Message
import io.opencola.network.providers.EventHandler
import io.opencola.network.providers.MessageHandler
import io.opencola.network.providers.NetworkProvider
import io.opencola.network.providers.ProviderEventType
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import mu.KotlinLogging
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.reflect.KClass

typealias messageHandler = (from: Id, to: Id, message: Message) -> List<Message>

// TODO: If another node suspends and (which looks offline) and then wakes up, other nodes will not be aware that it's
//  back online. Ping when coming out of suspend, or ping / request transactions periodically?

class NetworkNode(
    private val config: NetworkConfig,
    var routes: List<Route>, // TODO: Make map
    private val addressBook: AddressBook,
    private val eventBus: EventBus,
) {
    class Route(val messageClass: KClass<out Message>, val handler: messageHandler)

    private val logger = KotlinLogging.logger("NetworkNode")
    private var started = false
    private val peerStatuses = ConcurrentHashMap<Id, PeerStatus>()

    private fun expectStarted() {
        if (!started)
            throw IllegalStateException("Not started")
    }

    private data class PeerStatus(val lastSeenEpochSeconds: Long? = null) {
        fun setLastSeenEpochSeconds(epochSeconds: Long): PeerStatus {
            return PeerStatus(epochSeconds)
        }

        companion object {
            val default = PeerStatus()
        }
    }

    fun validatePeerAddress(address: URI) {
        if (address.toString().isBlank()) throw IllegalArgumentException("Address cannot be empty")
        val scheme = address.scheme ?: throw IllegalArgumentException("Address must contain a scheme")
        val provider = providers[scheme] ?: throw IllegalArgumentException("No provider for: ${address.scheme}")

        if (!config.offlineMode)
            provider.validateAddress(address)
    }

    @Synchronized
    // TODO: Should this just be removed for now? Was used to figure out when to request transactions, but
    //  not sure if that's needed anymore
    private fun updatePeerStatus(
        peerId: Id,
        update: (PeerStatus) -> PeerStatus
    ): PeerStatus? {
        val peer = addressBook.getEntries().firstOrNull { it.entityId == peerId }

        // TODO: Is this check needed?
        if (peer == null) {
            logger.warn("Attempt to update status for unknown peer: $peerId")
            return null
        }

        peerStatuses.getOrDefault(peerId, PeerStatus.default).let { previousStatus ->
            val newStatus = update(previousStatus)

            if (newStatus != previousStatus) {
                logger.info { "Updating peer ${peer.name} to $newStatus" }
                peerStatuses[peerId] = newStatus
            }

            return previousStatus
        }
    }

    private fun touchLastSeen(peerId: Id) {
        updatePeerStatus(peerId) { it.setLastSeenEpochSeconds(System.currentTimeMillis() / 1000) }
    }

    private val providers = ConcurrentHashMap<String, NetworkProvider>()

    private val eventHandler: EventHandler = { event ->
        when (event.type) {
            ProviderEventType.NO_PENDING_MESSAGES -> {
                val noPendingMessagesEvent = event as NoPendingMessagesEvent
                val personaId = noPendingMessagesEvent.personaId
                eventBus.sendMessage(Events.NoPendingNetworkMessages.toString(), personaId.encodeProto())
            }
        }
    }

    private val messageHandler: MessageHandler = { from, to, message ->
        if (addressBook.getEntry(to, from) !is PersonaAddressBookEntry)
            touchLastSeen(from)

        val peer = addressBook.getEntry(to, from)
            ?: throw IllegalArgumentException("Received request from unknown peer (from: $from to $to)")

        if (!peer.isActive)
            throw IllegalArgumentException("Received request from inactive peer (from: $from to: $to)")

        val persona = addressBook.getEntry(to, to) as? PersonaAddressBookEntry
            ?: throw IllegalArgumentException("Received request to invalid persona (from: $from to: $to)")

        if (!persona.isActive)
            throw IllegalArgumentException("Received request to inactive persona (from: $from to: $to)")

        try {
            val handler = routes.firstOrNull { it.messageClass == message::class }?.handler
                ?: throw IllegalArgumentException("No handler for \"${message::class.simpleName}\"")

            handler(from, to, message).forEach { response ->
                // Handler provided a response, so send it back
                sendMessage(to, from, response)
            }
        } catch (e: Throwable) {
            logger.error { "Error handling $message: $e" }
        }
    }

    fun addProvider(provider: NetworkProvider) {
        val scheme = provider.getScheme()
        if (providers.contains(scheme)) {
            throw IllegalArgumentException("Provider already registered: $scheme")
        }

        // TODO: Check not already started?

        provider.setEventHandler(eventHandler)
        provider.setMessageHandler(messageHandler)
        providers[scheme] = provider

        if (!config.offlineMode && started)
            provider.start()
    }

    // TODO: Make consistent with sendMessage, which currently uses ids publicly, but has a private method with address book entries
    fun broadcastMessage(from: PersonaAddressBookEntry, message: Message) {
        expectStarted()
        if (config.offlineMode) return

        val peers = addressBook.getEntries()
            .filter { it.personaId == from.personaId && it !is PersonaAddressBookEntry && it.isActive && providers[it.address.scheme] != null }
            .distinctBy { it.entityId }
            .toSet()

        if (peers.isNotEmpty()) {
            logger.info { "Broadcasting message - from: ${from.entityId} message: $message}" }
            // TODO: This is currently called in the background from the event bus, so ok, but
            //  should switch to making these requests from a pool of peer threads
            sendMessage(from, peers, message)
        }
    }

    private fun getProvider(peer: AddressBookEntry): NetworkProvider? {
        val provider = providers[peer.address.scheme]

        if (provider == null)
            logger.warn { "No provider for ${peer.address}" }

        return provider
    }

    private fun addPeer(peer: AddressBookEntry) {
        getProvider(peer)?.addPeer(peer)
            ?: logger.error { "No provider for ${peer.address}" }
    }

    private fun removePeer(peer: AddressBookEntry) {
        getProvider(peer)?.removePeer(peer)
            ?: logger.error { "No provider for ${peer.address}" }
    }

    private val peerUpdateHandler: (AddressBookEntry?, AddressBookEntry?) -> Unit =
        { previousAddressBookEntry, currentAddressBookEntry ->
            if (previousAddressBookEntry is PersonaAddressBookEntry || currentAddressBookEntry is PersonaAddressBookEntry) {
                // Do nothing - Since personas are local, so they don't affect peer connections
            } else if (previousAddressBookEntry != null && currentAddressBookEntry != null
                && previousAddressBookEntry.isActive != currentAddressBookEntry.isActive
            ) {
                if (previousAddressBookEntry.isActive)
                    removePeer(previousAddressBookEntry)
                else
                    addPeer(currentAddressBookEntry)
            } else if (previousAddressBookEntry?.address != currentAddressBookEntry?.address) {
                previousAddressBookEntry?.let { if (it.isActive) removePeer(it) }
                currentAddressBookEntry?.let { if (it.isActive) addPeer(it) }
            }
        }

    fun start(waitUntilReady: Boolean = false) {
        if (started) throw IllegalStateException("Already started")
        started = true

        if (config.offlineMode) {
            logger.warn { "Offline mode enabled, not starting network node" }
            return
        }

        logger.info { "Starting..." }
        providers.values.forEach { it.start(waitUntilReady) }
        addressBook.addUpdateHandler(peerUpdateHandler)
        logger.info { "Started" }
    }

    fun stop() {
        expectStarted()
        if (config.offlineMode) return

        logger.info { "Stopping..." }
        addressBook.removeUpdateHandler(peerUpdateHandler)
        providers.values.forEach { it.stop() }
        logger.info { "Stopped" }
        started = false
    }

    // @Synchronized
    private fun sendMessage(from: PersonaAddressBookEntry, to: Set<AddressBookEntry>, message: Message) {
        require(from.isActive) { "Attempt to send request from inactive persona: $from" }

        to.forEach {
            require(it !is PersonaAddressBookEntry) { "Attempt to send request to local persona: $it" }
            require(it.personaId == from.personaId) { "Attempt to send request to unknown peer: $it" }
            require(it !is PersonaAddressBookEntry) { "Attempt to send request to local persona: $it" }
            require(it.isActive) { "Attempt to send request to inactive peer: $it" }
            require(providers[it.address.scheme] != null) { "No provider found for peer: $it scheme: ${it.address.scheme}" }
        }

        if (config.offlineMode) return

        to.groupBy { it.address.scheme }.forEach { (scheme, peers) ->
            providers[scheme]!!.sendMessage(from, peers.toSet(), message)
        }
    }

    fun sendMessage(from: Id, to: Set<Id>, message: Message) {
        expectStarted()

        val persona = addressBook.getEntry(from, from) as? PersonaAddressBookEntry
            ?: throw IllegalArgumentException("Attempt to send from message from non Persona: $from")

        val peers = addressBook.getPeers()
            .filter { it.isActive && it.personaId == persona.entityId && to.contains(it.entityId) }
            .toSet()

        val missingIds = to - peers.map { it.entityId }.toSet()
        if (missingIds.isNotEmpty())
            throw IllegalArgumentException("Attempt to send message to unknown peers: ${missingIds.joinToString()}")

        sendMessage(persona, peers, message)
    }

    fun sendMessage(from: Id, to: Id, message: Message) {
        expectStarted()
        sendMessage(from, setOf(to), message)
    }
}