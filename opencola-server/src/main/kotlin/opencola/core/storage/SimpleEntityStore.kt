package opencola.core.storage

import opencola.core.extensions.ifNotNullOrElse
import opencola.core.extensions.nullOrElse
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

class SimpleEntityStore(val path: Path, addressBook: AddressBook , authority: Authority, signator: Signator) : AbstractEntityStore(authority, addressBook, signator) {
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

    override fun getLastTransactionId(authorityId: Id): Id? {
        return transactions
            .lastOrNull { it.transaction.authorityId == authorityId }
            .nullOrElse { it.transaction.id }
    }

    override fun getNextTransactionId(authorityId: Id): Id {
        return transactions
            .lastOrNull { it.transaction.authorityId == authorityId }
            .ifNotNullOrElse({ Id.ofData(SignedTransaction.encode(it)) }, { getFirstTransactionId(authorityId) })
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

    override fun persistTransaction(signedTransaction: SignedTransaction) : SignedTransaction{
        if(transactions.any{ it.transaction.id == signedTransaction.transaction.id})
            throw IllegalArgumentException("Attempt to insert duplicate transaction: ${signedTransaction.transaction.id}")

        path.outputStream(StandardOpenOption.APPEND, StandardOpenOption.CREATE)
            .use { SignedTransaction.encode(it, signedTransaction) }
        transactions = transactions + signedTransaction
        facts = facts + signedTransaction.transaction.expandFacts()
        return signedTransaction
    }

    override fun getTransactions(authorityId: Id, startTransactionId: Id?, numTransactions: Int): Iterable<SignedTransaction> {
        return transactions
            .filter { it.transaction.authorityId == authorityId }
            .dropWhile { if(startTransactionId != null) it.transaction.id != startTransactionId else false }
            .take(numTransactions)
    }

    override fun resetStore(): SimpleEntityStore {
        path.deleteIfExists()
        return SimpleEntityStore(path, addressBook, authority, signator)
    }
}