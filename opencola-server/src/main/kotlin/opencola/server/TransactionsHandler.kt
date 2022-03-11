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

//TODO: Pick a stance on handler functions vs. classes. Leaning towards functions, but currently inconsistent
// Pass OCApplicationCall around, if convenience methods needed
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
        val currentTransactionId = entityStore.getTransactionId(authorityId)
        val transactions = if (transactionId != null) entityStore.getTransactions(authorityId, transactionId) else emptyList()

        // TODO: Getting a request is a sign the the remote host is up - update the peer status in the PeerService
        call.respond(TransactionsResponse(transactionId, currentTransactionId, transactions.toList()))
    }
}