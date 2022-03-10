package opencola.core.storage

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import opencola.core.config.Application
import opencola.core.config.Network
import opencola.core.model.Entity
import opencola.core.model.Id
import opencola.core.model.SignedTransaction



class NetworkedEntityStore(private val entityStore: EntityStore, private val networkConfig: Network) : EntityStore {
    val logger = Application.instance.logger
    val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }


    init{
        // TODO: Request any new transactions from peers (in the background)
    }


    override fun getEntity(authorityId: Id, entityId: Id): Entity? {
        return entityStore.getEntity(authorityId, entityId)
    }

    private suspend fun broadcastTransaction(signedTransaction: SignedTransaction?) : SignedTransaction?{
        if(signedTransaction != null) {
            networkConfig.peers.forEach {
                logger.info { "Sending transaction {${signedTransaction.transaction.id}} to ${it.name}@${it.ip}" }
                // https://github.com/ktorio/ktor-documentation/blob/main/codeSnippets/snippets/client-json-kotlinx/src/main/kotlin/com/example/Application.kt

                try {
                    val response = httpClient.post<HttpStatement>("http://${it.ip}/transactions") {
                        contentType(ContentType.Application.Json)
                        body = listOf(signedTransaction)
                    }.execute()

                    logger.info { "Response: ${response.status}" }
                } catch (e: Exception){
                    logger.error { e.message }
                }
            }
        }

        return signedTransaction
    }

    override fun commitChanges(vararg entities: Entity): SignedTransaction? {
        val signedTransaction = entityStore.commitChanges(*entities)

        // TODO: Run async
        runBlocking {
            async { broadcastTransaction(entityStore.commitChanges(*entities)) }
        }

        return signedTransaction
    }

    override fun persistTransaction(signedTransaction: SignedTransaction) {
        entityStore.persistTransaction(signedTransaction)
    }

    override fun getTransaction(authorityId: Id, transactionId: Long): SignedTransaction? {
        return entityStore.getTransaction(authorityId, transactionId)
    }

    override fun getTransactions(
        authorityId: Id,
        startTransactionId: Long,
        endTransactionId: Long
    ): Iterable<SignedTransaction> {
        return entityStore.getTransactions(authorityId, startTransactionId, endTransactionId)
    }

    override fun getTransactionId(): Long {
        return entityStore.getTransactionId()
    }

    override fun resetStore(): EntityStore {
        return entityStore.resetStore()
    }
}