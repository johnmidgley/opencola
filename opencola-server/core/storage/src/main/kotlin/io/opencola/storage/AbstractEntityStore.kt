package io.opencola.storage

import mu.KotlinLogging
import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.model.*
import io.opencola.security.PublicKeyProvider
import io.opencola.util.ifNotNullOrElse
import io.opencola.util.nullOrElse
import io.opencola.security.Signator
import io.opencola.storage.EntityStore.TransactionOrder

// TODO: Should support multiple authorities
abstract class AbstractEntityStore(
    val signator: Signator,
    val publicKeyProvider: PublicKeyProvider<Id>,
    val eventBus: EventBus?,
    ) : EntityStore {
    // TODO: Assumes transaction has been validated. Cleanup?
    protected abstract fun persistTransaction(signedTransaction: SignedTransaction): Long

    private fun getFirstTransactionId(authorityId: Id): Id {
        // TODO: Random, or rooted with authority?
        return Id.ofData("$authorityId.firstTransaction".toByteArray())
    }

    // TODO: Make logger class?
    protected val logger = KotlinLogging.logger("EntityStore")
    protected fun logAndThrow(exception: Exception) {
        logger.error { exception.message }
        throw exception
    }

    // TODO - make entity method?
    private fun validateEntity(entity: Entity): Entity {
        val authorityIds = entity.getAllFacts().map { it.authorityId }.distinct()

        if (authorityIds.size != 1) {
            logAndThrow(RuntimeException("Entity{${entity.entityId}} contains facts from multiple authorities $authorityIds }"))
        }

        val authorityId = authorityIds.single()
        if (entity.authorityId != authorityId) {
            logAndThrow(RuntimeException("Entity{${entity.entityId}} with authority ${entity.authorityId} contains facts from wrong authority $authorityId }"))
        }

        val invalidEntityIds = entity.getAllFacts().filter { it.entityId != entity.entityId }.map { it.entityId }
        if (invalidEntityIds.isNotEmpty()) {
            logAndThrow(RuntimeException("Entity Id:{${entity.entityId}} contains facts not matching its id: $invalidEntityIds"))
        }

        if (entity.getAllFacts().distinct().size < entity.getAllFacts().size) {
            logAndThrow(RuntimeException("Entity Id:{${entity.entityId}} contains non-distinct facts"))
        }

        // TODO: Check that all transaction ids exist (0 to current) and don't surpass the current transaction id
        // TODO: Check that subsequent facts (by transactionId) for the same property are not equal
        // TODO: Check for duplicate facts (and add unit tests)
        return entity
    }

    override fun updateEntities(vararg entities: Entity): SignedTransaction? {
        entities.forEach { validateEntity(it) }

        if (entities.distinctBy { it.entityId }.size != entities.size) {
            logAndThrow(RuntimeException("Attempt to commit changes to multiple entities with the same id."))
        }

        val authorityIds = entities.map { it.authorityId }.toSet()

        if(authorityIds.size != 1){
            logAndThrow(RuntimeException("Attempt to commit changes to multiple authorities."))
        }

        val authorityId = authorityIds.first()

        if (!signator.canSign(authorityId.toString())) {
            logAndThrow(RuntimeException("Attempt to commit changes for authority without private key."))
        }

        val uncommittedFacts = entities.flatMap { it.getAllFacts() }.filter { it.transactionOrdinal == null }
        if (uncommittedFacts.isEmpty()) {
            logger.info { "Ignoring update with no novel facts" }
            return null
        }

        val (signedTransaction, transactionOrdinal) = persistTransaction(authorityId , uncommittedFacts)

        entities.forEach {
            it.commitFacts(signedTransaction.transaction.epochSecond, transactionOrdinal)
        }

        return signedTransaction
    }

    override fun getEntities(authorityIds: Set<Id>, entityIds: Set<Id>): List<Entity> {
        return getFacts(authorityIds, entityIds)
            .groupBy { Pair(it.authorityId, it.entityId) }
            .mapNotNull { Entity.fromFacts(it.value) }
    }

    private  fun computedFacts(facts: Iterable<Fact>) : List<Fact> {
        return CoreAttribute.values().flatMap { attribute ->
            attribute.spec.computeFacts.ifNotNullOrElse({ it(facts) }, { emptyList() })
        }
    }

    private fun validFact(facts: List<Fact>, fact: Fact): Boolean {
        return when (fact.operation) {
            // Don't allow superfluous adds
            Operation.Add ->
                !facts.any {
                    it.authorityId == fact.authorityId
                            && it.entityId == fact.entityId
                            && it.attribute == fact.attribute
                            && it.operation == fact.operation
                            && it.value.bytes.contentEquals(fact.value.bytes)
                }
            Operation.Retract ->
                // Don't allow superfluous retractions
                facts.any {
                    it.authorityId == fact.authorityId
                            && it.entityId == fact.entityId
                            && it.attribute == fact.attribute
                            && it.operation == Operation.Add
                            && (it.attribute.type == AttributeType.SingleValue || it.value.bytes.contentEquals(fact.value.bytes))
                }
        }
    }

    private fun validateFacts(authorityId: Id, facts: List<Fact>) : List<Fact> {
        // TODO: Since there are already "bad" facts out there, this will likely create an issue of blowing
        //  up anybody that gets bad facts. Figure out how to fix. Likely need to rebuild transaction chain then
        //  dis/reconnect to peers. Other option is to gracefully handle bad facts, but only from peers
        val transactionFactsByEntity = facts.groupBy { it.entityId }
        val existingEntities = getEntities(setOf(authorityId), transactionFactsByEntity.keys)

        existingEntities.forEach{ entity ->
            val currentFacts = entity.getCurrentFacts()
            val transactionsFacts = transactionFactsByEntity[entity.entityId]!!

            if (transactionsFacts.any { !validFact(currentFacts, it) }){
                throw IllegalArgumentException("Detected duplicate fact")
            }
        }

        return facts
    }

    // TODO: !!! Clean up validation !!!

    // DO NOT use this outside of persistTransaction
    private fun getNextTransactionId(authorityId: Id) : Id {
        return getSignedTransactions(listOf(authorityId), null, TransactionOrder.IdDescending, 1)
            .firstOrNull()
            .ifNotNullOrElse({ Id.ofData(SignedTransaction.encode(it)) }, { getFirstTransactionId(authorityId) })
    }

    // It is critical that this function is synchronized and not bypassed. It determines the next transaction
    // id, which needs to be unique, and does a final consistency / conflict check that can't be done in the DB
    @Synchronized
    private fun persistTransaction(authorityId: Id, facts: List<Fact>) : Pair<SignedTransaction, Long> {
        // TODO: Move validate to here
        val allFacts = validateFacts(authorityId, facts.plus(computedFacts(facts)))
        val signedTransaction = Transaction.fromFacts(getNextTransactionId(authorityId), allFacts).sign(signator)
        val transactionOrdinal = persistTransaction(signedTransaction)
        eventBus?.sendMessage(Events.NewTransaction.toString(), SignedTransaction.encode(signedTransaction))

        return Pair(signedTransaction, transactionOrdinal)
    }

    private fun getDeletedValue(fact: Fact) : Value {
        return if(fact.attribute.type != AttributeType.SingleValue
            || fact.attribute == CoreAttribute.Type.spec
            || fact.attribute == CoreAttribute.ParentId.spec)
            fact.value
        else
            Value.emptyValue
    }
    
    override fun deleteEntity(authorityId: Id, entityId: Id) {
        getEntity(authorityId, entityId).nullOrElse { entity ->
            val facts = entity.getCurrentFacts()
                .map { Fact(authorityId, entityId, it.attribute, getDeletedValue(it), Operation.Retract) }

            persistTransaction(authorityId, facts)
        }
    }

    override fun addSignedTransactions(signedTransactions: List<SignedTransaction>) {
        signedTransactions.forEach {
            val transactionAuthorityId = it.transaction.authorityId
            val publicKey =  publicKeyProvider.getPublicKey(transactionAuthorityId)
                ?: throw IllegalArgumentException("No public key for: $transactionAuthorityId - cannot persist transaction ${it.transaction.id}")

            if (!it.isValidTransaction(publicKey))
                throw IllegalArgumentException("Transaction ${it.transaction.id} failed to validate from $transactionAuthorityId")

            persistTransaction(it)
            eventBus?.sendMessage(Events.NewTransaction.toString(), SignedTransaction.encode(it))
        }
    }
}