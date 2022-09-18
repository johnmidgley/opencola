package io.opencola.core.network

import io.opencola.core.event.EventBus
import io.opencola.core.event.Events
import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import io.opencola.core.network.NetworkNode.PeerStatus.*
import io.opencola.core.network.providers.zerotier.ZeroTierNetworkProvider
import io.opencola.core.security.Encryptor
import io.opencola.core.storage.AddressBook
import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import io.opencola.core.config.NetworkConfig as OpenColaNetworkConfig

// TODO: If another node suspends and (which looks offline) and then wakes up, other nodes will not be aware that it's
//  back online. Ping when coming out of suspend, or ping / request transactions periodically?

class NetworkNode(
    private val config: OpenColaNetworkConfig,
    private val storagePath: Path,
    private val authorityId: Id,
    private val router: RequestRouter,
    private val addressBook: AddressBook,
    private val encryptor: Encryptor,
    private val eventBus: EventBus,
) {
    private val logger = KotlinLogging.logger("NetworkNode")
    private val peerStatuses = ConcurrentHashMap<Id, PeerStatus>()

    private enum class PeerStatus {
        Unknown,
        Offline,
        Online,
    }

    @Synchronized
    private fun updatePeerStatus(peerId: Id, status: PeerStatus, suppressNotifications: Boolean = false): PeerStatus {
        val peer = addressBook.getAuthority(peerId)

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

    private val requestHandler: (Request) -> Response = { request ->
        val response = router.handleRequest(request)

        // Since we received a request, the peer must be online
        if(request.from != authorityId)
            updatePeerStatus(request.from, Online)

        response
    }

    fun setProvider(scheme: String, provider: NetworkProvider?) {
        if(provider == null)
            providers.remove(scheme)?.stop()
        else {
            provider.setRequestHandler(requestHandler)
            providers[scheme] = provider
            provider.start()
        }
    }

    fun broadcastRequest(request: Request) {
        val peers = addressBook.getAuthorities(true)
        if (peers.isNotEmpty()) {
            logger.info { "Broadcasting request: $request" }

            // TODO: This is currently called in the background from the event bus, so ok, but
            //  should switch to making these requests from a pool of peer threads
            peers.forEach { peer ->
                if (listOf(Unknown, Online).contains(peerStatuses.getOrDefault(peer.entityId, Unknown))) {
                    // TODO: Make batched, to limit simultaneous connections
                    sendRequest(peer, request)
                }
            }
        }
    }

    // TODO: Move these AuthToken functions out of here. Should happen in peer handler.
    private fun getAuthToken(authority: Authority) : String? {
        return authority.networkToken.nullOrElse { String(encryptor.decrypt(authorityId, it)) }
    }

    // Only meant to be called from peerUpdateHandler
    private fun setAuthToken(authority: Authority) {
        if (authority.entityId != authorityId) {
            logger.warn { "Attempt to set auth token for non root authority" }
            return
        }

        val authToken = getAuthToken(authority)
        setProvider("zt", null)

        if (authToken != null) {
            val provider = ZeroTierNetworkProvider(storagePath, config.zeroTierConfig, authority, router, authToken)
            setProvider("zt", provider)
            authority.uri = provider.getAddress()

            // Avoid update recursion
            addressBook.updateAuthority(authority, suppressUpdateHandler = peerUpdateHandler)
        }
    }

    private val peerUpdateHandler : (Authority?, Authority?) -> Unit = { previousAuthority, currentAuthority ->
        // TODO: Move this.
        if(currentAuthority?.entityId == authorityId) {
            setAuthToken(currentAuthority)
        }

        if(previousAuthority?.uri != currentAuthority?.uri) {
            previousAuthority?.uri?.let {
                providers[it.scheme]?.removePeer(previousAuthority) ?:
                    logger.warn { "No provider for scheme: ${it.scheme} - can't remove peer with uri $it" }
            }

            currentAuthority?.uri?.let {
                providers[it.scheme]?.addPeer(currentAuthority) ?:
                    logger.warn { "No provider for scheme: ${it.scheme} - can't add peer with uri $it" }
            }
        }
    }

    fun start() {
        logger.info { "Starting..." }

        if(config.zeroTierConfig.providerEnabled) {
            val authority = addressBook.getAuthority(authorityId)
                ?: throw IllegalArgumentException("Root authority not in AddressBook: $authorityId")

            setAuthToken(authority)
        }

        providers.values.forEach { it.start() }

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
    private fun sendRequest(peer: Authority, request: Request) : Response? {
        // TODO: Dispatch based on peer provider
        if(request.from != authorityId){
            throw IllegalArgumentException("Cannot send request from a non local authority: ${request.from}")
        }

        val peerUri = peer.uri
        if(peerUri == null) {
            logger.warn { "Ignoring peer without uri: ${peer.entityId}" }
            return null
        }

        val provider = providers[peerUri.scheme] ?: throw IllegalStateException("No provider found for scheme: ${peerUri.scheme}")
        val response = provider.sendRequest(peer, request)

        // TODO: This is really bad. If we successfully get transactions when the user was in an offline/unknown state,
        //  setting their state to online would trigger another transactions request, which we want to avoid. Doing it
        //  this way is super ugly. Figure out a better way
        val suppressNotifications = request.path == "/transactions"

        if (response == null || response.status < 400)
            // Don't update status for calls that made it to a peer but result in an error. In particular, this
            // avoids a peer transition from offline to online when a call fails for authorization reasons
            updatePeerStatus(peer.entityId, if (response == null) Offline else Online, suppressNotifications)

        return response
    }

    // TODO - peer should be Authority or peerId?
    fun sendRequest(peerId: Id, request: Request) : Response? {
        val peer = addressBook.getAuthority(peerId)
        if(peer == null){
            logger.warn { "Attempt to send request to unknown peer" }
            return null
        }

        return sendRequest(peer, request)?.also {
            if (it.status >= 400) {
                logger.warn { it }
            }
        }
    }
}