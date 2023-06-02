package io.opencola.storage

import io.opencola.event.EventBus
import io.opencola.model.Fact
import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.security.PublicKeyProvider
import io.opencola.security.Signator
import io.opencola.serialization.EncodingFormat
import io.opencola.storage.entitystore.AbstractEntityStore
import io.opencola.storage.entitystore.EntityStore.TransactionOrder

class MockEntityStore(
    signator: Signator,
    publicKeyProvider: PublicKeyProvider<Id>,
    eventBus: EventBus? = null,
) : AbstractEntityStore(signator, publicKeyProvider, eventBus, EncodingFormat.PROTOBUF) {
    private val facts = mutableListOf<Fact>()
    private var transactionOrdinal = 0L
    data class TransactionInfo(val ordinal: Long, val signedTransaction: SignedTransaction)
    private val transactions = mutableListOf<TransactionInfo>()

    override fun persistTransaction(signedTransaction: SignedTransaction): Long {
        val transaction = signedTransaction.transaction
        val authorityId = transaction.authorityId
        val epochSecond = transaction.epochSecond
        val transactionOrdinal = transactionOrdinal++

        transaction.transactionEntities.flatMap { entity ->
            entity.facts.map {
                Fact(
                    authorityId,
                    entity.entityId,
                    it.attribute,
                    it.value,
                    it.operation,
                    epochSecond,
                    transactionOrdinal
                )
            }
        }.let {
            facts.addAll(it)
        }

        transactions.add(TransactionInfo(transactionOrdinal, signedTransaction))

        return transactionOrdinal
    }

    override fun getFacts(authorityIds: Set<Id>, entityIds: Set<Id>): List<Fact> {
        return facts.filter {
            authorityIds.contains(it.authorityId) && entityIds.contains(it.entityId)
        }
    }

    private fun sortBySelector(transactionOrder: TransactionOrder): (TransactionInfo) -> Long {
        return when (transactionOrder) {
            TransactionOrder.IdAscending -> { it -> it.ordinal }
            TransactionOrder.IdDescending -> { it -> -it.ordinal }
            TransactionOrder.TimeAscending -> { it -> it.signedTransaction.transaction.epochSecond }
            TransactionOrder.TimeDescending -> { it -> -it.signedTransaction.transaction.epochSecond }
        }
    }

    override fun getSignedTransactions(
        authorityIds: Set<Id>,
        startTransactionId: Id?,
        order: TransactionOrder,
        limit: Int
    ): Iterable<SignedTransaction> {
        val sortBySelector = sortBySelector(order)
        return transactions
            .filter { authorityIds.isEmpty() || authorityIds.contains(it.signedTransaction.transaction.authorityId) }
            .sortedBy { sortBySelector(it) }
            .let { transactionInfos ->
                if (startTransactionId != null) {
                    transactionInfos.dropWhile { it.signedTransaction.transaction.id != startTransactionId }
                } else {
                    transactionInfos
                }
            }
            .map { it.signedTransaction }
            .take(limit)
    }
}