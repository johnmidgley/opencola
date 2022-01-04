package opencola.core.storage

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KotlinLogging
import opencola.core.model.*
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class SimpleEntityStore(authority: Authority, private val path: Path) : EntityStore(authority) {
    private val logger = KotlinLogging.logger {}
    private fun logAndThrow(exception: Exception) {
        logger.error { exception.message }
        throw exception
    }

    // TODO: Synchronize access
    private var facts = emptyList<Fact>()

    init {
        if(!path.exists()){
            logger.warn { "No entity store found at $path. Will get created on update" }
        } else {
            facts = transactions(path)
                .filter { isValidTransaction(it) }
                .flatMap { it.transaction.getFacts() }
                .toList()
        }

        setEpoch(facts.filter { it.authorityId == authority.entityId }.map { it.transactionId }.maxOrNull()?.inc() ?: 0)
    }

    private fun transactions(path: Path): Sequence<SignedTransaction> {
        return sequence {
            path.inputStream().use{
                // TODO: Make sure available works properly. From the docs, it seems like it can return 0 when no buffered data left.
                // Can't find the idiomatic way to check for end of file. May need to read until exception??
                while(it.available() > 0)
                    yield(SignedTransaction.decode(it))

                if(it.read() != -1){
                    logAndThrow(RuntimeException("While reading transactions, encountered available() == 0 but read() != -1"))
                }
            }
        }
    }

    override fun getEntity(authority: Authority, entityId: Id): Entity? {
        // TODO: Entity.fromFacts
        return Entity.getInstance(facts.filter { it.authorityId == authority.entityId && it.entityId == entityId }.toList())
    }

    override fun persistTransaction(signedTransaction: SignedTransaction) {
        this.path.outputStream(StandardOpenOption.APPEND, StandardOpenOption.CREATE).use { SignedTransaction.encode(it, signedTransaction) }
        facts = facts + signedTransaction.expandFacts()
    }
}