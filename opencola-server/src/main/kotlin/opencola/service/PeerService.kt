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
import opencola.core.model.SignedTransaction
import opencola.core.network.Peer
import opencola.core.storage.EntityStore
import opencola.core.storage.NetworkedEntityStore
import opencola.server.TransactionsHandler

class PeerService(private val networkConfig: NetworkConfig, private val entityStore: EntityStore) {
    private val logger = KotlinLogging.logger{}
    private val peers = peersFromNetworkConfig(networkConfig)

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

    init{
        if(entityStore is NetworkedEntityStore){
            throw IllegalArgumentException("PeerService should not be instantiated with NetworkedEntityStore - could cause transaction loops")
        }

        // TODO: verify async
        runBlocking {
            peers.forEach{
                async { requestTransactions(it.value) }
            }
        }
    }

    // TODO: Peers should eventually come from a private part of the entity store
    private fun peersFromNetworkConfig(networkConfig: NetworkConfig): Map<Id, Peer> {
        return networkConfig.peers.map{
            val peerId = Id.fromHexString(it.id)
            Pair(peerId, Peer(Id.fromHexString(it.id), it.name, it.host, Peer.Status.Offline))
        }.toMap()
    }

    fun broadcastTransaction(signedTransaction: SignedTransaction?): SignedTransaction? {
        if (signedTransaction != null) {
            runBlocking {
                peers.forEach {
                    // TODO: Make batched, to limit simultaneous connections
                    async { sendTransaction(it.value, signedTransaction) }
                }
            }
        }

        return signedTransaction
    }

    private suspend fun sendTransaction(peer: Peer, signedTransaction: SignedTransaction){
        logger.info { "Sending transaction {${signedTransaction.transaction.id}} to ${peer.name}@${peer.host}" }

        try {
            val response = httpClient.post<HttpStatement>("http://${peer.host}/transactions") {
                contentType(ContentType.Application.Json)
                body = listOf(signedTransaction)
            }.execute()

            logger.info { "Response: ${response.status}" }
            peer.status = Peer.Status.Online
        } catch (e: Exception){
            logger.error { e.message }
            peer.status = Peer.Status.Offline
        }

    }

    private suspend fun requestTransactions(peer: Peer){
        // TODO: Update getTransaction to take authorityId
        var currentTransactionId = entityStore.getTransactionId(peer.id)

        try {
            // TODO - Config match batches
            for(batch in 1..10) {
                val urlString = "http://${peer.host}/transactions/${peer.id}/$currentTransactionId"
                logger.info { "Requesting transactions - Batch $batch - $urlString" }

                val transactionsResponse: TransactionsHandler.TransactionsResponse =
                    httpClient.get(urlString)

                peer.status = Peer.Status.Online

                transactionsResponse.transactions.forEach {
                    entityStore.persistTransaction(it)
                }

                currentTransactionId = transactionsResponse.transactions.maxOf { it.transaction.id }

                if(currentTransactionId == transactionsResponse.currentTransactionId)
                    break
            }
        } catch (e: Exception){
            logger.error { e.message }
            // TODO: This should depend on the error
            peer.status = Peer.Status.Offline
        }
    }

}