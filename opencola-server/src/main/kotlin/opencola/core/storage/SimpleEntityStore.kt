package opencola.core.storage

import opencola.core.model.Authority
import opencola.core.model.Entity
import opencola.core.model.Id
import opencola.core.model.SignedTransaction
import opencola.core.security.Signator
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class SimpleEntityStore(val path: Path, authority: Authority, signator: Signator) : AbstractEntityStore(authority, signator) {
    // TODO: Synchronize access
    private var transactions =
        if (!path.exists()) {
            logger.warn { "No entity store found at $path. Will get created on update" }
            emptyList()
        } else {
            transactionsFromPath(path).toList()
        }

    private var facts =
        transactions
            .filter { isValidTransaction(it) }
            .flatMap { it.transaction.getFacts() }
            .toList()

    init {
        setTransactiondId(transactions.lastOrNull()?.transaction?.id ?: 0)
    }

    private fun transactionsFromPath(path: Path): Sequence<SignedTransaction> {
        return sequence {
            path.inputStream().use {
                // TODO: Make sure available works properly. From the docs, it seems like it can return 0 when no buffered data left.
                // Can't find the idiomatic way to check for end of file. May need to read until exception??
                while (it.available() > 0)
                    yield(SignedTransaction.decode(it))

                if (it.read() != -1) {
                    logAndThrow(RuntimeException("While reading transactions, encountered available() == 0 but read() != -1"))
                }
            }
        }
    }

    override fun getEntity(authorityId: Id, entityId: Id): Entity? {
        // TODO: Entity.fromFacts
        return Entity.getInstance(facts.filter { it.authorityId == authorityId && it.entityId == entityId }.toList())
    }

    override fun persistTransaction(signedTransaction: SignedTransaction) {
        path.outputStream(StandardOpenOption.APPEND, StandardOpenOption.CREATE)
            .use { SignedTransaction.encode(it, signedTransaction) }
        transactions += signedTransaction
        facts += signedTransaction.expandFacts()
    }

    override fun getTransaction(authorityId: Id, transactionId: Long): SignedTransaction? {
        // TODO: Super inefficient. If this needs to be used, have transactions loaded to memory.
        return transactions
            .filter { it.transaction.authorityId == authorityId && it.transaction.id == transactionId }
            .firstOrNull()
    }

    override fun getTransactions(authorityId: Id, startTransactionId: Long, endTransactionId: Long): Iterable<SignedTransaction> {
        return transactions
            .filter { it.transaction.authorityId == authorityId
                    && it.transaction.id >= startTransactionId
                    && it.transaction.id <= endTransactionId }
    }

    override fun resetStore(): SimpleEntityStore {
        path.deleteIfExists()
        return SimpleEntityStore(path, authority, signator)
    }
}