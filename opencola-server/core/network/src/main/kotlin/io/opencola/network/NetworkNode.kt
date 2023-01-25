package io.opencola.network

import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.model.Authority
import io.opencola.model.Id
import io.opencola.model.Persona
import io.opencola.network.NetworkNode.PeerStatus.*
import io.opencola.storage.AddressBook
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
        val peer = addressBook.getAuthorities().firstOrNull { it.entityId == peerId }

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
        if(addressBook.getAuthority(to, from) !is Persona)
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

    fun broadcastRequest(from: Persona, request: Request) {
        val peers = addressBook.getAuthorities(true)
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

    private fun getProvider(peer: Authority) : NetworkProvider? {
        val provider = peer.uri?.let { providers[it.scheme] }

        if(provider == null)
            logger.warn { "No provider for ${peer.uri}" }

        return provider
    }

    private fun addPeer(peer: Authority) {
        getProvider(peer)?.addPeer(peer)
    }

    private fun removePeer(peer: Authority) {
        getProvider(peer)?.removePeer(peer)
    }

    private val peerUpdateHandler: (Authority?, Authority?) -> Unit = { previousAuthority, currentAuthority ->
        if (previousAuthority != null && currentAuthority != null
            && previousAuthority.getActive() != currentAuthority.getActive()
        ) {
            if (previousAuthority.getActive())
                removePeer(previousAuthority)
            else
                addPeer(currentAuthority)
        } else if (previousAuthority?.uri != currentAuthority?.uri) {
            previousAuthority?.let { if(it.getActive()) removePeer(it) }
            currentAuthority?.let { if(it.getActive()) addPeer(it) }
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

    // TODO - peer should be Authority or peerId?
    private fun sendRequest(from: Persona, to: Authority, request: Request) : Response? {
        val peerUri = to.uri
        if(peerUri == null) {
            logger.warn { "Ignoring sendRequest to peer without uri: ${to.entityId}" }
            return null
        }

        val provider = providers[peerUri.scheme] ?: throw IllegalStateException("No provider found for scheme: ${peerUri.scheme}")
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

    // TODO - peer should be Authority or peerId?
    fun sendRequest(fromId: Id, toId: Id, request: Request) : Response? {
        val persona = addressBook.getAuthority(fromId, fromId) as? Persona
            ?: throw IllegalArgumentException("Can't send from message from non Persona")
        val peer = addressBook.getAuthority(fromId, toId)
            ?: throw IllegalArgumentException("Attempt to send request to unknown peer")

        // TODO: Authority should be passed in
        return sendRequest(persona, peer, request)?.also {
            if (it.status >= 400) {
                logger.warn { it }
            }
        }
    }
}