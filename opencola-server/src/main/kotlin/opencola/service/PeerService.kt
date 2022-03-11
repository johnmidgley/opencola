package opencola.service

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
import opencola.core.network.Peer

class PeerService(private val networkConfig: NetworkConfig) {
    private val logger = KotlinLogging.logger{}
    private val idToPeerMap = peersFromNetworkConfig(networkConfig)
    val peers: List<Peer> get() { return idToPeerMap.values.toList() }

    enum class Event {
        NewTransactions
    }

    @Serializable
    data class Notification(val authorityId: Id, val event: Event)

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

    // TODO: Peers should eventually come from a private part of the entity store
    private fun peersFromNetworkConfig(networkConfig: NetworkConfig): MutableMap<Id, Peer> {
        return networkConfig.peers.associate {
            val peerId = Id.fromHexString(it.id)
            Pair(peerId, Peer(Id.fromHexString(it.id), it.name, it.host, Peer.Status.Offline))
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

    fun updateStatus(peer: Peer, status: Peer.Status){
        val peer = idToPeerMap[peer.id] ?: throw IllegalArgumentException("Attempt to update status for unknown peer: ${peer.id}")

        // UGGhhh. No persistent data structures...
        idToPeerMap[peer.id] = peer.setStatus(status)
    }

    private suspend fun sendMessage(peer: Peer, path: String, message: Any){
        val urlString = "http://${peer.host}/$path"
        logger.info { "Sending $message to $urlString" }

        try {
            val response = httpClient.post<HttpStatement>(urlString) {
                contentType(ContentType.Application.Json)
                body = message
            }.execute()

            logger.info { "Response: ${response.status}" }
            // peer.status = Peer.Status.Online
        } catch (e: Exception){
            logger.error { e.message }
            // peer.status = Peer.Status.Offline
        }
    }
}