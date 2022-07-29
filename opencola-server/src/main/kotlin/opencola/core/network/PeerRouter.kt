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
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.network.PeerRouter.PeerStatus.*
import opencola.core.serialization.StreamSerializer
import opencola.core.serialization.readInt
import opencola.core.serialization.writeInt
import opencola.core.storage.AddressBook
import opencola.server.handlers.TransactionsResponse
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

// TODO: Should respond to changes in address book
class PeerRouter(private val addressBook: AddressBook, private val eventBus: EventBus) {
    private val logger = KotlinLogging.logger("PeerRouter")
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

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

    fun getPeer(peerId: Id) : Authority? {
        return addressBook.getAuthority(peerId)
    }

    fun getPeers() : Set<Authority> {
        return addressBook.getAuthorities(true)
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

            val url = "${peer.uri}/transactions/${peer.entityId}${peerTransactionId.ifNotNullOrElse({ "/${it}" },{ "" })}?peerId=${authority.authorityId}"
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
}