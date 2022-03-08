package opencola.server

import io.ktor.application.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import opencola.core.extensions.nullOrElse
import opencola.core.model.Id
import opencola.core.model.SignedTransaction
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