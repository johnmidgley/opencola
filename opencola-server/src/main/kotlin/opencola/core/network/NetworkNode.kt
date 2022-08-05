package opencola.core.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.core.event.EventBus
import opencola.core.event.Events
import opencola.core.extensions.ifNotNullOrElse
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.network.NetworkNode.PeerStatus.*
import opencola.core.network.providers.zerotier.ZeroTierNetworkProvider
import opencola.core.security.Encryptor
import opencola.core.serialization.StreamSerializer
import opencola.core.serialization.readInt
import opencola.core.serialization.writeInt
import opencola.core.storage.AddressBook
import opencola.server.handlers.Peer
import opencola.server.handlers.TransactionsResponse
import opencola.server.handlers.redactedNetworkToken
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
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

    enum class PeerStatus {
        Unknown,
        Offline,
        Online,
    }

    enum class Event {
        Added,
        Online,
        NewTransaction
    }

    @Serializable
    data class Notification(val peerId: Id, val event: Event)  {
        fun encode() : ByteArray {
            return Factory.encode(this)
        }

        companion object Factory : StreamSerializer<Notification> {
            override fun encode(stream: OutputStream, value: Notification) {
                Id.encode(stream, value.peerId)
                stream.writeInt(value.event.ordinal)
            }

            override fun decode(stream: InputStream): Notification {
                // TODO: Could throw exception
                return Notification(Id.decode(stream), Event.values()[stream.readInt()])
            }
        }
    }

    // TODO: Move to HTTPNetworkProvider
    private val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

    fun broadcastMessage(path: String, message: Any){
        runBlocking {
            val peers = addressBook.getAuthorities(true)
            if(peers.isNotEmpty()) {
                logger.info { "Broadcasting message: $message" }

                peers.forEach {
                    if (listOf(Unknown, Online).contains(peerStatuses.getOrDefault(it.entityId, Unknown))) {
                        // TODO: Make batched, to limit simultaneous connections
                        @Suppress("DeferredResultUnused")
                        async { sendMessage(it, path, message) }
                    }
                }
            }
        }
    }

    // TODO: This should be private and updated based on calls / responses
    fun updateStatus(peerId: Id, status: PeerStatus, suppressNotifications: Boolean = false): PeerStatus {
        val peer = addressBook.getAuthority(peerId)
            ?: throw IllegalArgumentException("Attempt to update status for unknown peer: $peerId")

        logger.info { "Updating peer ${peer.name} to $status" }

        peerStatuses.getOrDefault(peerId, Unknown).let { previousStatus ->
            peerStatuses[peerId] = status

            if (!suppressNotifications && status != previousStatus && status == Online) {
                eventBus.sendMessage(Events.PeerNotification.toString(), Notification(peerId, Event.Online).encode())
            }

            return previousStatus
        }
    }

    suspend fun getTransactions(authority: Authority, peer: Authority, peerTransactionId: Id?): TransactionsResponse? {
        try {
            // TODO: Should not allow getTransactions for local authority
            if(!addressBook.isAuthorityActive(peer)){
                logger.warn { "Ignoring getTransactions for inactive peer: ${peer.entityId}" }
                return null
            }

            val peerUri = peer.uri

            if(peerUri == null){
                logger.warn { "Ignoring peer without uri: ${peer.entityId}" }
                return null
            }

            if(peerUri.scheme != "http"){
                logger.warn { "Ignoring non http peer: $peerUri" }
                return null
            }

            val url = "${peerUri}/transactions/${peer.entityId}${peerTransactionId.ifNotNullOrElse({ "/${it}" },{ "" })}?peerId=${authority.authorityId}"
            val response: TransactionsResponse = httpClient.get(url)

            // Suppress notifications, otherwise will trigger another transactions request
            // TODO: Seems a bit messy. Is there a cleaner way to handle switch to online
            //  without having to specify suppression?
            updateStatus(peer.entityId, Online, true)

            return response
        } catch (e: Exception) {
            if(e is java.net.ConnectException)
                logger.info { "${peer.name} appears to be offline." }
            else
                logger.error { e.message }
            // TODO: This should depend on the error
            updateStatus(peer.entityId, Offline)
        }

        return null
    }

    // TODO: Break this out by message. It's exposing to much that you can send a message to an arbitrary path
    private suspend fun sendMessage(peer: Authority, path: String, message: Any) {
        try {
            if(!addressBook.isAuthorityActive(peer)) {
                logger.warn { "Ignoring message to inactive peer: ${peer.entityId}" }
                return
            }

            val urlString = "${peer.uri}/$path"
            logger.info { "Sending $message to $urlString" }

            val response = httpClient.post<HttpStatement>(urlString) {
                contentType(ContentType.Application.Json)
                body = message
            }.execute()

            logger.info { "Response: ${response.status}" }

            peerStatuses[peer.entityId] = Online
        }
        catch(e: java.net.ConnectException){
            logger.info { "${peer.name} appears to be offline." }
            peerStatuses[peer.entityId] = Offline
        }
        catch (e: Exception){
            logger.error { e.message }
            peerStatuses[peer.entityId] = Offline
        }
    }

    // TODO: Make install script put the platform dependent version of libzt in the right place. On mac, it needs to be
    //  put in ~/Library/Java/Extensions/ (or try /Library/Java/Extensions/ globally)
    //  Need to figure out where it goes on Linux / Windows

    // TODO - Make nullable and only set when enabled (Search for all config.zeroTierProviderEnabled)
    private var zeroTierNetworkProvider: ZeroTierNetworkProvider? = null

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
        zeroTierNetworkProvider.nullOrElse { it.stop() }

        if (authToken != null) {
            zeroTierNetworkProvider = ZeroTierNetworkProvider(storagePath, config.zeroTierConfig, authority, router, authToken)
            zeroTierNetworkProvider!!.start()
            authority.uri = zeroTierNetworkProvider!!.getAddress()

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
        zeroTierNetworkProvider.nullOrElse { it.stop() }
        logger.info { "Stopped" }
    }

    fun getInviteToken() : InviteToken {
        val authority = addressBook.getAuthority(authorityId)
            ?: throw IllegalStateException("Root authority not found - can't generate invite token")
        return InviteToken.fromAuthority(authority)
    }

    fun inviteTokenToPeer(inviteToken: String) : Peer {
        val decodedInviteToken = InviteToken.decodeBase58(inviteToken)
        val imageUri = if(decodedInviteToken.imageUri.toString().isBlank()) null else decodedInviteToken.imageUri

        if(decodedInviteToken.authorityId == authorityId)
            throw IllegalArgumentException("You can't invite yourself (┛ಠ_ಠ)┛彡┻━┻")

        return Peer(
            decodedInviteToken.authorityId,
            decodedInviteToken.name,
            decodedInviteToken.publicKey,
            decodedInviteToken.address,
            imageUri,
            true,
            null,
        )
    }

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
                zeroTierNetworkProvider.nullOrElse { it.removePeer(existingPeerAuthority) }
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
        zeroTierNetworkProvider.nullOrElse { it.updatePeer(peerToUpdate) }
        addressBook.updateAuthority(peerToUpdate)

        if (existingPeerAuthority == null)
            // New peer has been added - request transactions
            eventBus.sendMessage(
                Events.PeerNotification.toString(),
                Notification(peerAuthority.entityId, Event.Online).encode()
            )
    }

    private fun removePeer(peerId: Id){
        logger.info { "Removing peer: $peerId" }
        val peer = addressBook.getAuthority(peerId)

        if(peer == null){
            logger.info { "No peer found - ignoring" }
            return
        }

        zeroTierNetworkProvider.nullOrElse { it.removePeer(peer) }
        addressBook.deleteAuthority(peerId)
    }


    fun sendRequest(peer: Authority, request: Request) : Response? {
        // TODO: Dispatch based on peer provider
        if(request.from != authorityId){
            throw IllegalArgumentException("Cannot send request from a non local authority: ${request.from}")
        }

        return zeroTierNetworkProvider!!.sendRequest(peer, request)
    }

}