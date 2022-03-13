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
import opencola.core.config.NetworkConfig
import opencola.core.model.Id
import opencola.core.network.Peer.*

class PeerRouter(private val networkConfig: NetworkConfig) {
    private val logger = KotlinLogging.logger("PeerRouter")
    private val idToPeerMap = peersFromNetworkConfig(networkConfig)
    val peers: List<Peer> get() { return idToPeerMap.values.map { it.toPeer() }.toList() }

    fun getPeer(peerId: Id): Peer? {
        return idToPeerMap[peerId]?.toPeer() ?: null
    }

    data class MutablePeer(val id: Id, val name: String, val host: String, var status: Status = Status.Unknown){
        fun toPeer(): Peer {
            return Peer(id, name, host, status)
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

    // TODO: Peers should eventually come from a private part of the entity store
    private fun peersFromNetworkConfig(networkConfig: NetworkConfig): MutableMap<Id, MutablePeer> {
        return networkConfig.peers.associate {
            val peerId = Id.fromHexString(it.id)
            Pair(peerId, MutablePeer(Id.fromHexString(it.id), it.name, it.host))
        }.toMutableMap()
    }

    fun broadcastMessage(path: String, message: Any){
        runBlocking {
            idToPeerMap.values.forEach {
                // TODO: Make batched, to limit simultaneous connections
                async { sendMessage(it, path, message) }
            }
        }
    }

    fun updateStatus(peerId: Id, status: Status){
        val peer = idToPeerMap[peerId] ?: throw IllegalArgumentException("Attempt to update status for unknown peer: $peerId")
        peer.status = status
    }

    private suspend fun sendMessage(peer: MutablePeer, path: String, message: Any){
        val urlString = "http://${peer.host}/$path"
        logger.info { "Sending $message to $urlString" }

        try {
            val response = httpClient.post<HttpStatement>(urlString) {
                contentType(ContentType.Application.Json)
                body = message
            }.execute()

            logger.info { "Response: ${response.status}" }
            peer.status = Status.Online
        } catch (e: Exception){
            logger.error { e.message }
            peer.status = Status.Offline
        }
    }
}