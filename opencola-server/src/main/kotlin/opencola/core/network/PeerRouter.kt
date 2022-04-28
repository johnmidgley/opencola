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
import opencola.core.model.Id
import opencola.core.model.Peer
import opencola.core.network.PeerRouter.PeerStatus.*
import opencola.core.network.PeerRouter.PeerStatus.Status.*
import opencola.core.serialization.StreamSerializer
import opencola.core.storage.AddressBook
import java.io.InputStream
import java.io.OutputStream

// TODO: Should respond to changes in address book
class PeerRouter(private val addressBook: AddressBook, private val eventBus: EventBus) {
    private val logger = KotlinLogging.logger("PeerRouter")
    private val peerIdToStatusMap = this.addressBook.peers.associate { Pair(it.id, PeerStatus(it)) }
    val peers: List<Peer> get() { return peerIdToStatusMap.values.map { it.peer }}

    fun getPeer(peerId: Id): Peer? {
        return peerIdToStatusMap[peerId]?.peer
    }

    data class PeerStatus(val peer: Peer, var status: Status = if (peer.active) Unknown else Offline){
        enum class Status{
            Unknown,
            Offline,
            Online
        }
    }

    enum class Event {
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
                writeInt(stream, value.event.ordinal)
            }

            override fun decode(stream: InputStream): Notification {
                // TODO: Could throw exception
                return Notification(Id.decode(stream), Event.values()[readInt(stream)])
            }
        }
    }

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

    fun broadcastMessage(path: String, message: Any){
        runBlocking {
            peerIdToStatusMap.values.forEach {
                if(listOf(Unknown, Online).contains(it.status) && it.peer.active) {
                    // TODO: Make batched, to limit simultaneous connections
                    async { sendMessage(it, path, message) }
                }
            }
        }
    }

    fun updateStatus(peerId: Id, status: Status, suppressNotifications: Boolean = false): Status {
        logger.info { "Updating peer $peerId to $status" }
        val peer = peerIdToStatusMap[peerId] ?: throw IllegalArgumentException("Attempt to update status for unknown peer: $peerId")
        val previousStatus = peer.status
        peer.status = status

        if(!suppressNotifications && status != previousStatus && status == Online){
            eventBus.sendMessage(Events.PeerNotification.toString(), Notification(peerId, Event.Online).encode())
        }

        return previousStatus
    }

    private suspend fun sendMessage(peerStatus: PeerStatus, path: String, message: Any){
        val urlString = "http://${peerStatus.peer.host}/$path"
        logger.info { "Sending $message to $urlString" }

        try {
            val response = httpClient.post<HttpStatement>(urlString) {
                contentType(ContentType.Application.Json)
                body = message
            }.execute()

            logger.info { "Response: ${response.status}" }

            peerStatus.status = Online
        }
        catch(e: java.net.ConnectException){
            logger.info { "${peerStatus.peer.name} appears to be offline." }
            peerStatus.status = Offline
        }
        catch (e: Exception){
            logger.error { e.message }
            peerStatus.status = Offline
        }
    }
}