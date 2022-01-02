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
        return sequence {
            path.readLines().forEach { line ->
                if(line.isNotEmpty()){
                    yield(Json.decodeFromString(line))
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
    private fun validateEntity(entity: Entity) {
        val authorityIds = entity.getFacts().map { it.authorityId }.distinct()

        if(authorityIds.size != 1){
            logAndThrow(RuntimeException("Entity{${entity.entityId}} contains facts from multiple authorities $authorityIds }"))
        }

        val authorityId = authorityIds.single()
        if(entity.authorityId != authorityId){
            logAndThrow(RuntimeException("Entity{${entity.entityId}} with authority ${entity.authorityId} contains facts from wrong authority $authorityId }"))
        }

        val invalidEntityIds = entity.getFacts().filter { it.entityId != entity.entityId }.map { it.entityId }
        if (invalidEntityIds.isNotEmpty()) {
            logAndThrow(RuntimeException("Entity Id:{${entity.entityId}} contains facts not matching its id: $invalidEntityIds"))
        }

        if(entity.getFacts().distinct().size < entity.getFacts().size){
            logAndThrow(RuntimeException("Entity Id:{${entity.entityId}} contains non-distinct facts"))
        }

        // TODO: Check that all transaction ids exist (0 to current) and don't surpass the current transaction id
        // TODO: Check that subsequent facts (by transactionId) for the same property are not equal
        // TODO: Check for duplicate facts (and add unit tests)
    }

    private fun getNextTransactionId(authority: Authority): Long {
        return facts.filter { it.authorityId == authority.entityId }.map { it.transactionId }.maxOrNull()?.inc() ?: 0
    }

    private fun commitTransaction(authority: Authority, uncommittedFacts: List<Fact>) : List<Fact> {
        val transactionId = getNextTransactionId(authority)

        this.path?.outputStream(StandardOpenOption.APPEND, StandardOpenOption.CREATE)?.use { outputStream ->
            // TODO: Binary serialization?
            Json.encodeToStream(
                authority.signTransaction(
                    Transaction.fromFacts(transactionId, uncommittedFacts)
                ),
                outputStream
            )
            outputStream.write("\n".toByteArray())
        }

        return uncommittedFacts.map { it.updateTransactionId(transactionId) }
    }

    private fun getAuthority(id: Id) : Authority {
        return trustedActors.firstOrNull { it.authorityId == id && it is Authority } as Authority?
            ?: throw RuntimeException("No authority with id $id")
    }

    // TODO: Make entity varargs so that multiple entities can be updated in a single transaction
    fun commitChanges(vararg entities: Entity)// : List<Entity>
    {
        entities.forEach { validateEntity(it) }

        if(entities.map{ it.authorityId }.distinct().size > 1){
            logAndThrow(RuntimeException("Attempt to update multiple entities with multiple authorities. Must make separate calls"))
        }

        val uncommittedFacts = entities.flatMap { it.getFacts() }.filter{ it.transactionId == UNCOMMITTED }
        val authority = getAuthority(entities.first().authorityId)

        if (uncommittedFacts.isEmpty()) {
            logger.info { "Ignoring update with no novel facts" }
            return
        }

        // TODO: Synchronized
        facts = facts + commitTransaction(authority, uncommittedFacts)
    }

    fun getEntity(authority: Authority, entityId: Id): Entity? {
        return Entity.getInstance(facts.filter { it.authorityId == authority.entityId && it.entityId == entityId }.toList())
    }
}