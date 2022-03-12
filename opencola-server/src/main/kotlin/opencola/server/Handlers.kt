package opencola.server

import io.ktor.application.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import opencola.core.extensions.nullOrElse
import opencola.core.model.Id
import opencola.core.model.SignedTransaction
import opencola.core.storage.EntityStore
import opencola.service.search.SearchService

suspend fun handleSearchCall(call: ApplicationCall, searchService: SearchService) {
    val query = call.request.queryParameters["q"] ?: throw IllegalArgumentException("No query (q) specified in parameters")
    call.respond(searchService.search(query))
}

suspend fun handleEntityCall(call: ApplicationCall, authorityId: Id, entityStore: EntityStore){
    // TODO: Authority should be passed (and authenticated) in header
    val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")

    val entity = entityStore.getEntity(authorityId, Id.fromHexString(stringId))

    if(entity != null)
        call.respond(entity.getFacts())
}

@Serializable
data class TransactionsResponse(
    val startTransactionId: Long?,
    val currentTransactionId: Long,
    val transactions: List<SignedTransaction>)

suspend fun handleTransactionsCall(call: ApplicationCall, entityStore: EntityStore){
    val authorityId = Id.fromHexString(call.parameters["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
    val transactionId = call.parameters["transactionId"].nullOrElse { it.toLong() }
    val currentTransactionId = entityStore.getTransactionId(authorityId)
    val transactions = if (transactionId != null) entityStore.getTransactions(authorityId, transactionId) else emptyList()

    // TODO: Getting a request is a sign the the remote host is up - update the peer status in the PeerService
    call.respond(TransactionsResponse(transactionId, currentTransactionId, transactions.toList()))
}