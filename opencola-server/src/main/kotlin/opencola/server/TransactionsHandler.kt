package opencola.server

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import opencola.core.config.Application
import opencola.core.extensions.nullOrElse
import opencola.core.model.Id
import opencola.core.model.SignedTransaction
import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore
import org.kodein.di.instance

class TransactionsHandler(call: ApplicationCall) : Handler(call){
    private val authorityId = Id.fromHexString(call.parameters["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
    private val transactionId = call.parameters["transactionId"].nullOrElse { it.toLong() }

    @Serializable
    data class TransactionsResponse(
        val startTransactionId: Long?,
        val currentTransactionId: Long,
        val transactions: List<SignedTransaction>)

    override suspend fun respond() {
        val entityStore by injector.instance<EntityStore>()
        val currentTransactionId = entityStore.getTransactionId()
        val transactions = if (transactionId != null) entityStore.getTransactions(authorityId, transactionId) else emptyList()

        call.respond(TransactionsResponse(transactionId, currentTransactionId, transactions.toList()))
    }
}

//TODO: Pick a stance on handler functions vs. classes. Leaning towards functions, but currently inconsistent
// Pass OCApplicationCall around, if convenience methods needed
suspend fun handlePostTransactions(app: Application, call: ApplicationCall){
    val entityStore by app.injector.instance<EntityStore>()
    val searchIndex by app.injector.instance<SearchIndex>()
    val transactions = call.receive<List<SignedTransaction>>()

    // TODO: All of this should be done in the background off a durable queue
    transactions.forEach{ st ->
        val authorityId = st.transaction.authorityId
        entityStore.persistTransaction(st)

        st.transaction.transactionFacts
            .map{ f -> f.entityId}
            .distinct()
            .forEach{ eid ->
                val entity = entityStore.getEntity(authorityId, eid)

                if(entity == null)
                    app.logger.error { "Can't get entity after persisting entity facts: $authorityId:$eid" }
                else{
                    //TODO: Archive will not be available - figure out what to do
                    // Call peer for data?
                    app.logger.info { "Indexing $authorityId:$eid" }
                    searchIndex.index(entity)
                }
            }
    }

    call.respond(HttpStatusCode.OK)
}