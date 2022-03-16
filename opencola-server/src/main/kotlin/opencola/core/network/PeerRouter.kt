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
import opencola.core.model.Id
import opencola.core.model.Peer
import opencola.core.network.PeerRouter.PeerStatus.Status.*
import opencola.core.storage.AddressBook

// TODO: Should respond to changes in address book
class PeerRouter(private val addressBook: AddressBook) {
    private val logger = KotlinLogging.logger("PeerRouter")
    private val peerIdToStatusMap = addressBook.peers.associate { Pair(it.id, PeerStatus(it)) }
    val peers: List<Peer> get() { return peerIdToStatusMap.values.map { it.peer }}

    fun getPeer(peerId: Id): Peer? {
        return peerIdToStatusMap[peerId]?.peer
    }

    data class PeerStatus(val peer: Peer, var status: Status = Unknown){
        enum class Status{
            Unknown,
            Offline,
            Online
        }
    }

    enum class Event {
        NewTransactions
    }

    @Serializable
    data class Notification(val peerId: Id, val event: Event)

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

    fun broadcastMessage(path: String, message: Any){
        runBlocking {
            peerIdToStatusMap.values.forEach {
                // TODO: Make batched, to limit simultaneous connections
                async { sendMessage(it, path, message) }
            }
        }
    }

    fun updateStatus(peerId: Id, status: PeerStatus.Status){
        val peer = peerIdToStatusMap[peerId] ?: throw IllegalArgumentException("Attempt to update status for unknown peer: $peerId")
        peer.status = status
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