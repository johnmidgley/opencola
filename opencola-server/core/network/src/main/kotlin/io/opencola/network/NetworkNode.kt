package io.opencola.network

import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.model.Id
import io.opencola.network.NetworkNode.PeerStatus.*
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
    private val router: RequestRouter,
    private val addressBook: AddressBook,
    private val eventBus: EventBus,
) {
    private val logger = KotlinLogging.logger("NetworkNode")
    private val peerStatuses = ConcurrentHashMap<Id, PeerStatus>()

    private enum class PeerStatus {
        Unknown,
        Offline,
        Online,
    }

    fun validatePeerAddress(address: URI) {
        if(address.toString().isBlank()) throw IllegalArgumentException("Address cannot be empty")
        val scheme = address.scheme ?: throw IllegalArgumentException("Address must contain a scheme")
        val provider = providers[scheme] ?: throw IllegalArgumentException("No provider for: ${address.scheme}")
        provider.validateAddress(address)
    }

    @Synchronized
    private fun updatePeerStatus(peerId: Id, status: PeerStatus, suppressNotifications: Boolean = false): PeerStatus {
        val peer = addressBook.getEntries().firstOrNull { it.entityId == peerId }

        // TODO: Is this check needed?
        if(peer == null) {
            logger.warn("Attempt to update status for unknown peer: $peerId")
            return Unknown
        }

        peerStatuses.getOrDefault(peerId, Unknown).let { previousStatus ->
            if(status != previousStatus) {
                logger.info { "Updating peer ${peer.name} to $status" }
                peerStatuses[peerId] = status

                // TODO: Why ignore anything but online?
                if (!suppressNotifications && status == Online) {
                    eventBus.sendMessage(
                        Events.PeerNotification.toString(),
                        Notification(peerId, PeerEvent.Online).encode()
                    )
                }
            }

            return previousStatus
        }
    }

    private val providers = ConcurrentHashMap<String, NetworkProvider>()

    private val requestHandler: (Id, Id, Request) -> Response = { from, to, request ->
        val response = router.handleRequest(from, to, request)

        // Since we received a request, the peer must be online
        if(addressBook.getEntry(to, from) !is PersonaAddressBookEntry)
            updatePeerStatus(from, Online)

        response
    }

    fun addProvider(provider: NetworkProvider) {
        val scheme = provider.getScheme()
        if(providers.contains(scheme)) {
            throw IllegalArgumentException("Provider already registered: $scheme")
        }

        // TODO: Check not already started?

        provider.setRequestHandler(requestHandler)
        providers[scheme] = provider
        provider.start()
    }

    fun broadcastRequest(from: PersonaAddressBookEntry, request: Request) {
        val peers = addressBook.getEntries()
            .filter { it !is PersonaAddressBookEntry && it.isActive }
            .distinctBy { it.entityId }

        if (peers.isNotEmpty()) {
            logger.info { "Broadcasting request: $request" }

            // TODO: This is currently called in the background from the event bus, so ok, but
            //  should switch to making these requests from a pool of peer threads
            peers.forEach { peer ->
                if (listOf(Unknown, Online).contains(peerStatuses.getOrDefault(peer.entityId, Unknown))) {
                    // TODO: Make batched, to limit simultaneous connections
                    sendRequest(from, peer, request)
                }
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
        logger.info { "Starting..." }
        providers.values.forEach { it.start(waitUntilReady) }
        addressBook.addUpdateHandler(peerUpdateHandler)
        logger.info { "Started" }
    }

    fun stop() {
        logger.info { "Stopping..." }
        addressBook.removeUpdateHandler(peerUpdateHandler)
        providers.values.forEach{ it.stop() }
        logger.info { "Stopped" }
    }

    private fun sendRequest(from: PersonaAddressBookEntry, to: AddressBookEntry, request: Request) : Response? {
        require(to !is PersonaAddressBookEntry)
        val scheme = to.address.scheme
        val provider = providers[scheme] ?: throw IllegalStateException("No provider found for scheme: $scheme")
        val response = provider.sendRequest(from, to, request)

        // TODO: This is really bad. If we successfully get transactions when the user was in an offline/unknown state,
        //  setting their state to online would trigger another transactions request, which we want to avoid. Doing it
        //  this way is super ugly. Figure out a better way
        val suppressNotifications = request.path == "/transactions"

        if (response == null || response.status < 400)
            // Don't update status for calls that made it to a peer but result in an error. In particular, this
            // avoids a peer transition from offline to online when a call fails for authorization reasons
            updatePeerStatus(to.entityId, if (response == null) Offline else Online, suppressNotifications)

        return response
    }

    fun sendRequest(fromId: Id, toId: Id, request: Request) : Response? {
        val persona = addressBook.getEntry(fromId, fromId) as? PersonaAddressBookEntry
            ?: throw IllegalArgumentException("Attempt to send from message from non Persona: $fromId")

        val peer = addressBook.getEntry(fromId, toId)
            ?: throw IllegalArgumentException("Attempt to send request to unknown peer: $toId")

        if(peer is PersonaAddressBookEntry)
            throw IllegalArgumentException("Attempt to send request to local persona: $peer")

        if(!(persona.isActive && peer.isActive))
            return null

        return sendRequest(persona, peer, request)?.also {
            if (it.status >= 400) {
                logger.warn { it }
            }
        }
    }
}