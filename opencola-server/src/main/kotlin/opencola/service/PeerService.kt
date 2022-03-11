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
import mu.KotlinLogging
import opencola.core.config.NetworkConfig
import opencola.core.model.Id
import opencola.core.network.Peer

class PeerService(private val networkConfig: NetworkConfig) {
    private val logger = KotlinLogging.logger{}
    private val idToPeerMap = peersFromNetworkConfig(networkConfig)
    val peers: List<Peer> get() { return idToPeerMap.values.toList() }

    enum class Event{
        NewTransactions
    }
    data class Notification(val authorityId: Id, val event: Event)

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

    // TODO: Peers should eventually come from a private part of the entity store
    private fun peersFromNetworkConfig(networkConfig: NetworkConfig): Map<Id, Peer> {
        return networkConfig.peers.map{
            val peerId = Id.fromHexString(it.id)
            Pair(peerId, Peer(Id.fromHexString(it.id), it.name, it.host, Peer.Status.Offline))
        }.toMap()
    }

    fun broadcastMessage(path: String, message: Any){
        runBlocking {
            idToPeerMap.values.forEach {
                // TODO: Make batched, to limit simultaneous connections
                async { sendMessage(it, path, message) }
            }
        }
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

//    private suspend fun sendNotification(peer: Peer, notification: Notification){
//        val urlString = "http://${peer.host}/notifications"
//        logger.info { "Sending notification $notification to $urlString" }
//
//        try {
//            val response = httpClient.post<HttpStatement>(urlString) {
//                contentType(ContentType.Application.Json)
//                body = notification // TODO: If body accepts any serializable object, could make this method generic, with extra path parameter
//            }.execute()
//
//            logger.info { "Response: ${response.status}" }
//                // peer.status = Peer.Status.Online
//        } catch (e: Exception){
//            logger.error { e.message }
//            // peer.status = Peer.Status.Offline
//        }
//    }


//    private suspend fun requestTransactions(peer: Peer){
//        // TODO: Update getTransaction to take authorityId
//        var currentTransactionId = entityStore.getTransactionId(peer.id)
//
//        try {
//            // TODO - Config match batches
//            for(batch in 1..10) {
//                val urlString = "http://${peer.host}/transactions/${peer.id}/$currentTransactionId"
//                logger.info { "Requesting transactions - Batch $batch - $urlString" }
//
//                val transactionsResponse: TransactionsHandler.TransactionsResponse =
//                    httpClient.get(urlString)
//
//                peer.status = Peer.Status.Online
//
//                transactionsResponse.transactions.forEach {
//                    entityStore.persistTransaction(it)
//                }
//
//                currentTransactionId = transactionsResponse.transactions.maxOf { it.transaction.id }
//
//                if(currentTransactionId == transactionsResponse.currentTransactionId)
//                    break
//            }
//        } catch (e: Exception){
//            logger.error { e.message }
//            // TODO: This should depend on the error
//            peer.status = Peer.Status.Offline
//        }
//    }

}