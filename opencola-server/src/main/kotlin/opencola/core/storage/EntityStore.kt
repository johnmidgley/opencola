package opencola.core.storage

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KotlinLogging
import opencola.core.model.*
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.PublicKey
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

class EntityStore(trustedActors: Set<ActorEntity>) {
    private val logger = KotlinLogging.logger {}
    private fun logAndThrow(exception: Exception) {
        logger.error { exception.message }
        throw exception
    }

    private var trustedActors = emptySet<ActorEntity>()
    private var path: Path? = null


    // TODO: Make a class that synchronizes updates to facts
    private var facts = emptyList<Fact>()

    init {
        addTrustedActors(trustedActors)
    }

    private fun addTrustedActors(actors: Set<ActorEntity>) {
        val partitionedActors = actors.partition { it.publicKey == null }

        if (partitionedActors.first.isNotEmpty()) {
            logger.error { "Ignoring trusted actors with no public key: ${partitionedActors.first.joinToString()}" }
        }

        this.trustedActors = this.trustedActors + partitionedActors.second
    }


    private fun transactions(path: Path): Sequence<SignedTransaction> {
        // TODO: Only works for JSON
        return sequence<SignedTransaction> {
            path.readLines().forEach { line ->
                if(line.isNotEmpty()){
                    val transaction = Json.decodeFromString<SignedTransaction>(line)
                    if (transaction != null){
                        yield(transaction)
                    }
                }
            }
        }
    }

    private fun isValidTransaction(signedTransaction: SignedTransaction): Boolean {
        // TODO: Move what can be moved to transaction
        val transactionId = signedTransaction.transaction.id
        val facts = signedTransaction.transaction.getFacts()

        val authority = signedTransaction.transaction.authorityId
        val actorEntity = trustedActors.firstOrNull { it.entityId == signedTransaction.transaction.authorityId }

        if (actorEntity == null) {
            // TODO: Load all public keys from the store first, in order to verify transactions?
            logger.warn { "Ignoring transaction $transactionId with unverifiable authority: $authority" }
            return false
        }

        if(signedTransaction.transaction.getFacts().any { it.transactionId == UNCOMMITTED}){
            // TODO: Throw or ignore?
            logAndThrow(IllegalStateException("Transaction has uncommitted id" ))
        }

        if (!signedTransaction.isValidTransaction(actorEntity.publicKey as PublicKey)) {
            logger.error { "Ignoring transaction with invalid signature $transactionId" }
        }

        return true
    }

    fun load(path: Path) {
        this.path = path

        if(!path.exists()){
            logger.warn { "No entity store found at $path. Will get created on update" }
        } else {
            facts = transactions(path)
                .filter { isValidTransaction(it) }
                .flatMap { it.transaction.getFacts() }
                .toList()
        }
    }

    // TODO - make entity method?
    private fun isEntityValid(authority: Authority, entity: Entity): Boolean {
        val invalidAuthorityIds = entity.getFacts().filter { it.authorityId != authority.entityId }.map { it.authorityId }
        if (invalidAuthorityIds.isNotEmpty()) {
            logger.error("Entity{${entity.entityId}} contains facts not matching authority{$authority.id}: $invalidAuthorityIds")
            return false
        }

        val invalidEntityIds = entity.getFacts().filter { it.entityId != entity.entityId }.map { it.entityId }
        if (invalidEntityIds.isNotEmpty()) {
            logger.error("Entity Id:{${entity.entityId}} contains facts not matching its id: $invalidEntityIds")
        }

        // TODO: Check that all transaction ids exist (0 to current) and don't surpass the current transaction id
        // TODO: Check that subsequent facts (by transactionId) for the same property are not equal
        // TODO: Check for duplicate facts (and add unit tests)

        return false
    }

    private fun getFactsToCommit(authority: Authority, entity: Entity): List<Fact> {
        // TODO: Check for duplicate facts (and add unit tests)
        val uncommittedFacts = entity.getFacts().filter { it.transactionId == UNCOMMITTED }

        val invalidAuthorityIds = uncommittedFacts.filter { it.authorityId != authority.entityId }.map { it.authorityId }
        if (invalidAuthorityIds.isNotEmpty()) {
            logAndThrow(IllegalArgumentException("Entity{${entity.entityId}} contains facts not matching authority{$authority.id}: $invalidAuthorityIds"))
        }

        val invalidEntityIds = uncommittedFacts.filter { it.entityId != entity.entityId }.map { it.entityId }
        if (invalidEntityIds.isNotEmpty()) {
            logAndThrow(IllegalArgumentException("Entity Id:{${entity.entityId}} contains facts not matching its id: $invalidEntityIds"))
        }

        // TODO: Make idempotent - i.e. ignore facts that are identical to most recent one

        return uncommittedFacts.distinct()
    }

    private fun getNextTransactionId(authority: Authority): Long {
        return facts.filter { it.authorityId == authority.entityId }.map { it.transactionId }.maxOrNull()?.inc() ?: 0
    }

    private fun saveTransaction(authority: Authority, uncommittedFacts: List<Fact>, path: Path) : List<Fact> {
        val transactionId = getNextTransactionId(authority)
        path.outputStream(StandardOpenOption.APPEND, StandardOpenOption.CREATE).use { outputStream ->
            // TODO: Binary serialization?
            Json.encodeToStream(
                authority.signTransaction(
                    Transaction(
                        authority.entityId,
                        uncommittedFacts,
                        transactionId
                    )
                ),
                outputStream
            )
            outputStream.write("\n".toByteArray())
        }

        return uncommittedFacts.map { it.updateTransactionId(transactionId) }
    }

    fun updateEntity(authority: Authority, entity: Entity): Entity {
        val uncommittedFacts = getFactsToCommit(authority, entity)
        var commitedFacts = uncommittedFacts

        if (uncommittedFacts.isEmpty()) {
            logger.info { "Ignoring update to entity:{${entity.entityId}} with no novel facts" }
            return entity
        }

        var transactionId = UNCOMMITTED

        val path = this.path
        if (path != null) {
            commitedFacts = saveTransaction(authority, uncommittedFacts, path)
        }

        // TODO: Synchronized
        facts = facts + commitedFacts

        return getEntity(authority, entity.entityId)
            ?: throw RuntimeException("Unable to find updated entity:{${entity.entityId}} in store")
    }

    fun getEntity(authority: Authority, entityId: Id): Entity? {
        return Entity.getInstance(facts.filter { it.authorityId == authority.entityId && it.entityId == entityId }.toList())
    }
}