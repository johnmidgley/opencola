package io.opencola.network

import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.model.Id
import io.opencola.network.message.UnsignedMessage
import io.opencola.network.message.SignedMessage
import io.opencola.security.Signator
import io.opencola.storage.AddressBook
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.PersonaAddressBookEntry
import mu.KotlinLogging
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

// TODO: If another node suspends and (which looks offline) and then wakes up, other nodes will not be aware that it's
//  back online. Ping when coming out of suspend, or ping / request transactions periodically?

class NetworkNode(
    private val config: NetworkConfig,
    private val router: RequestRouter,
    private val addressBook: AddressBook,
    private val eventBus: EventBus,
    private val signator: Signator,
) {
    private val logger = KotlinLogging.logger("NetworkNode")
    private val peerStatuses = ConcurrentHashMap<Id, PeerStatus>()

    private data class PeerStatus(val lastSeenEpochSeconds: Long? = null, val waitingForTransactions: Boolean = false) {
        fun setLastSeenEpochSeconds(epochSeconds: Long): PeerStatus {
            return PeerStatus(epochSeconds, waitingForTransactions)
        }

        fun setWaitingForTransactions(waitingForTransactions: Boolean): PeerStatus {
            return PeerStatus(lastSeenEpochSeconds, waitingForTransactions)
        }

        companion object {
            val default = PeerStatus()
        }
    }

    fun validatePeerAddress(address: URI) {
        if(address.toString().isBlank()) throw IllegalArgumentException("Address cannot be empty")
        val scheme = address.scheme ?: throw IllegalArgumentException("Address must contain a scheme")
        val provider = providers[scheme] ?: throw IllegalArgumentException("No provider for: ${address.scheme}")

        if(!config.offlineMode)
            provider.validateAddress(address)
    }

    @Synchronized
    private fun updatePeerStatus(
        peerId: Id,
        suppressNotifications: Boolean = false,
        update: (PeerStatus) -> PeerStatus
    ): PeerStatus? {
        val peer = addressBook.getEntries().firstOrNull { it.entityId == peerId }

        // TODO: Is this check needed?
        if(peer == null) {
            logger.warn("Attempt to update status for unknown peer: $peerId")
            return null
        }

        peerStatuses.getOrDefault(peerId, PeerStatus.default).let { previousStatus ->
            val newStatus = update(previousStatus)

            if(newStatus != previousStatus) {
                logger.info { "Updating peer ${peer.name} to $newStatus" }
                peerStatuses[peerId] = newStatus

                // TODO: Why ignore anything but online?
                if (!suppressNotifications && previousStatus.lastSeenEpochSeconds == null && newStatus.lastSeenEpochSeconds != null) {
                    eventBus.sendMessage(
                        Events.PeerNotification.toString(),
                        Notification(peerId, PeerEvent.Online).encode()
                    )
                }
            }

            return previousStatus
        }
    }

    private fun touchLastSeen(peerId: Id) {
        updatePeerStatus(peerId) { it.setLastSeenEpochSeconds(System.currentTimeMillis() / 1000) }
    }

    private val providers = ConcurrentHashMap<String, NetworkProvider>()

    private val requestHandler: (Id, Id, SignedMessage) -> Unit = { from, to, signedMessage ->
        try {
            router.handleRequest(from, to, signedMessage)
        } catch (e: Exception) {
            logger.error(e) { "Error handling signedMessage: $signedMessage" }
        }
        if(addressBook.getEntry(to, from) !is PersonaAddressBookEntry)
            touchLastSeen(from)

        TODO("Check if caller is trapping exceptions")
    }

    fun addProvider(provider: NetworkProvider) {
        val scheme = provider.getScheme()
        if(providers.contains(scheme)) {
            throw IllegalArgumentException("Provider already registered: $scheme")
        }

        // TODO: Check not already started?

        provider.setRequestHandler(requestHandler)
        providers[scheme] = provider

        if(!config.offlineMode)
            provider.start()
    }

    fun broadcastMessage(from: PersonaAddressBookEntry, message: UnsignedMessage) {
        if(config.offlineMode) return

        val peers = addressBook.getEntries()
            .filter { it.personaId == from.personaId && it !is PersonaAddressBookEntry && it.isActive }
            .distinctBy { it.entityId }

        if (peers.isNotEmpty()) {
            logger.info { "Broadcasting request: ${message.type}" }

            // TODO: This is currently called in the background from the event bus, so ok, but
            //  should switch to making these requests from a pool of peer threads
            peers.forEach { peer ->
                // TODO: Make batched, to limit simultaneous connections
                sendMessage(from, peer, message)
            }
        }
    }

    private fun getProvider(peer: AddressBookEntry) : NetworkProvider? {
        val provider = providers[peer.address.scheme]

        if(provider == null)
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

    private val peerUpdateHandler: (AddressBookEntry?, AddressBookEntry?) -> Unit = { previousAddressBookEntry, currentAddressBookEntry ->
        if(previousAddressBookEntry is PersonaAddressBookEntry || currentAddressBookEntry is PersonaAddressBookEntry) {
            // Do nothing - Since personas are local, so they don't affect peer connections
        } else if (previousAddressBookEntry != null && currentAddressBookEntry != null
            && previousAddressBookEntry.isActive != currentAddressBookEntry.isActive
        ) {
            if (previousAddressBookEntry.isActive)
                removePeer(previousAddressBookEntry)
            else
                addPeer(currentAddressBookEntry)
        } else if (previousAddressBookEntry?.address != currentAddressBookEntry?.address) {
            previousAddressBookEntry?.let { if(it.isActive) removePeer(it) }
            currentAddressBookEntry?.let { if(it.isActive) addPeer(it) }
        }
    }

    fun start(waitUntilReady: Boolean = false) {
        if(config.offlineMode) {
            logger.warn { "Offline mode enabled, not starting network node" }
            return
        }

        logger.info { "Starting..." }
        providers.values.forEach { it.start(waitUntilReady) }
        addressBook.addUpdateHandler(peerUpdateHandler)
        logger.info { "Started" }
    }

    fun stop() {
        if(config.offlineMode) return

        logger.info { "Stopping..." }
        addressBook.removeUpdateHandler(peerUpdateHandler)
        providers.values.forEach{ it.stop() }
        logger.info { "Stopped" }
    }

    fun signMessage(from: PersonaAddressBookEntry, message: UnsignedMessage): SignedMessage {
        return SignedMessage(
            from.personaId,
            message,
            signator.signBytes(from.personaId.toString(), message.payload)
        )
    }

    private fun sendMessage(from: PersonaAddressBookEntry, to: AddressBookEntry, message: UnsignedMessage) {
        require(to !is PersonaAddressBookEntry)
        if(config.offlineMode) return

        val provider = to.address.scheme.let { scheme ->
            providers[scheme] ?: throw IllegalStateException("No provider found for scheme: $scheme")
        }

        provider.sendMessage(from, to, signMessage(from, message))
    }

    fun sendMessage(fromId: Id, toId: Id, message: UnsignedMessage) {
        if(config.offlineMode) return

        val persona = addressBook.getEntry(fromId, fromId) as? PersonaAddressBookEntry
            ?: throw IllegalArgumentException("Attempt to send from message from non Persona: $fromId")

        val peer = addressBook.getEntry(fromId, toId)
            ?: throw IllegalArgumentException("Attempt to send request to unknown peer: $toId")

        if(peer is PersonaAddressBookEntry)
            throw IllegalArgumentException("Attempt to send request to local persona: $peer")

        if(!(persona.isActive && peer.isActive))
            return

        sendMessage(persona, peer, message)
    }
}