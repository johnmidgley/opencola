package opencola.core.network

import mu.KotlinLogging
import opencola.core.event.EventBus
import opencola.core.event.Events
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.network.NetworkNode.PeerStatus.*
import opencola.core.network.providers.zerotier.ZeroTierNetworkProvider
import opencola.core.security.Encryptor
import opencola.core.storage.AddressBook
import opencola.server.handlers.Peer
import opencola.server.handlers.redactedNetworkToken
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.listOf
import kotlin.collections.set
import opencola.core.config.NetworkConfig as OpenColaNetworkConfig


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

    // TODO: This should be private and updated based on calls / responses
    private fun updateStatus(peerId: Id, status: PeerStatus, suppressNotifications: Boolean = false): PeerStatus {
        val peer = addressBook.getAuthority(peerId)

        if(peer == null) {
            logger.warn("Attempt to update status for unknown peer: $peerId")
            return Unknown
        }

        logger.info { "Updating peer ${peer.name} to $status" }

        peerStatuses.getOrDefault(peerId, Unknown).let { previousStatus ->
            peerStatuses[peerId] = status

            if (!suppressNotifications && status != previousStatus && status == Online) {
                eventBus.sendMessage(Events.PeerNotification.toString(), Notification(peerId, PeerEvent.Online).encode())
            }

            return previousStatus
        }
    }

    private val providers = ConcurrentHashMap<String, NetworkProvider>()

    private val requestHandler: (Request) -> Response = { request ->
        val response = router.handleRequest(request)

        // Since we received a request, the peer must be online
        updateStatus(request.from, Online)

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
            val provider = ZeroTierNetworkProvider(storagePath, config.zeroTierConfig, authority, addressBook, router, authToken)
            setProvider("zt", provider)
            authority.uri = provider.getAddress()

            // Avoid update recursion
            addressBook.updateAuthority(authority, suppressUpdateHandler = peerUpdateHandler)
        }
    }

    private val peerUpdateHandler : (Authority) -> Unit = { peer ->
        if(peer.entityId == authorityId) {
            setAuthToken(peer)
        }
    }

    fun isNetworkTokenValid(networkToken: String) : Boolean {
        return ZeroTierNetworkProvider.isNetworkTokenValid(networkToken)
    }

    fun start() {
        logger.info { "Starting..." }

        if(config.zeroTierConfig.providerEnabled) {
            val authority = addressBook.getAuthority(authorityId)
                ?: throw IllegalArgumentException("Root authority not in AddressBook: $authorityId")

            setAuthToken(authority)
        }

        addressBook.addUpdateHandler(peerUpdateHandler)
        logger.info { "Started" }
    }

    fun stop() {
        logger.info { "Stopping..." }
        addressBook.removeUpdateHandler(peerUpdateHandler)
        providers.values.forEach{ it.stop() }
        logger.info { "Stopped" }
    }

    // TODO: Should not be here. Peer is a client API class. Something else should map to Authority and do the update,
    //  then those that rely on updates (Providers) should subscribe to addressBook changes
    fun updatePeer(peer: Peer) {
        logger.info { "Updating peer: $peer" }

        val peerAuthority = peer.toAuthority(authorityId, encryptor)
        val existingPeerAuthority = addressBook.getAuthority(peerAuthority.entityId)

        if(existingPeerAuthority != null) {
            logger.info { "Found existing peer - updating" }

            if(existingPeerAuthority.publicKey != peerAuthority.publicKey){
                throw NotImplementedError("Updating publicKey is not currently implemented")
            }

            if(existingPeerAuthority.uri != peerAuthority.uri) {
                // Since address is being updated, remove zero tier connection for old address
                existingPeerAuthority.uri?.let { providers[it.scheme]?.removePeer(existingPeerAuthority) }
            }

            // TODO: Should there be a general way to do this? Add an update method to Entity or Authority?
            existingPeerAuthority.name = peerAuthority.name
            existingPeerAuthority.publicKey = peerAuthority.publicKey
            existingPeerAuthority.uri = peerAuthority.uri
            existingPeerAuthority.imageUri = peerAuthority.imageUri
            existingPeerAuthority.tags = peerAuthority.tags
            existingPeerAuthority.networkToken = peerAuthority.networkToken
        }

        if(peer.networkToken != null){
            if(peerAuthority.entityId != authorityId){
                throw IllegalArgumentException("Attempt to set networkToken for non root authority")
            }

            if(peer.networkToken != redactedNetworkToken) {
                if(!isNetworkTokenValid(peer.networkToken)){
                    throw IllegalArgumentException("Network token provided is not valid: ${peer.networkToken}")
                }

                peerAuthority.networkToken = encryptor.encrypt(authorityId, peer.networkToken.toByteArray())
            }
        }

        val peerToUpdate = existingPeerAuthority ?: peerAuthority
        peerToUpdate.uri?.let { providers[it.scheme]?.updatePeer(peerToUpdate) }
        addressBook.updateAuthority(peerToUpdate)

        if (existingPeerAuthority == null)
            // New peer has been added - request transactions
            eventBus.sendMessage(
                Events.PeerNotification.toString(),
                Notification(peerAuthority.entityId, PeerEvent.Online).encode()
            )
    }

    private fun removePeer(peerId: Id){
        logger.info { "Removing peer: $peerId" }
        val peer = addressBook.getAuthority(peerId)

        if(peer == null){
            logger.info { "No peer found - ignoring" }
            return
        }

        peer.uri?.let { providers[it.scheme]?.removePeer(peer) }
        addressBook.deleteAuthority(peerId)
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

        if(suppressNotifications)
            logger.warn { "Suppressing notifications" }

        if (response == null || response.status < 400)
            // Don't update status for calls that made it to a peer but result in an error. In particular, this
            // avoids a peer transition from offline to online when a call fails for authorization reasons
            updateStatus(peer.entityId, if (response == null) Offline else Online, suppressNotifications)

        return response
    }

    // TODO - peer should be Authority or peerId?
    fun sendRequest(peerId: Id, request: Request) : Response? {
        val peer = addressBook.getAuthority(peerId)
        if(peer == null){
            logger.warn { "Attempt to send request to unknown peer" }
            return null
        }

        return sendRequest(peer, request)
    }
}