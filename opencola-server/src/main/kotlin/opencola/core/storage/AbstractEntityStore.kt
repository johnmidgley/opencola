package opencola.core.storage

import mu.KotlinLogging
import opencola.core.model.*
import opencola.core.security.Signator
import java.security.PublicKey

private const val INVALID_TRANSACTION_ID: Long = -1

// TODO: Should support multiple authorities
abstract class AbstractEntityStore(val authority: Authority, protected val signator: Signator) : EntityStore {
    // TODO: Make logger class?
    protected val logger = KotlinLogging.logger {}
    protected fun logAndThrow(exception: Exception) {
        logger.error { exception.message }
        throw exception
    }
    private fun logAndThrow(message: String){
        logAndThrow(RuntimeException(message))
    }

    private var transactionId: Long = INVALID_TRANSACTION_ID

    @Synchronized
    protected fun setTransactiondId(transactiondId: Long){
        if(this.transactionId != INVALID_TRANSACTION_ID){
            logAndThrow("Attempt to transaction id that has already been set")
        }

        this.transactionId = transactiondId
    }

    override fun getTransactionId(): Long {
        return transactionId
    }

    protected fun isValidTransaction(signedTransaction: SignedTransaction): Boolean {
        // TODO: Move what can be moved to transaction
        val transactionId = signedTransaction.transaction.id
        val facts = signedTransaction.transaction.getFacts()


        if (signedTransaction.transaction.authorityId != authority.entityId) {
            logger.warn { "Ignoring transaction $transactionId with unverifiable authority: $authority" }
            return false
        }

        if(signedTransaction.transaction.getFacts().any { it.transactionId == UNCOMMITTED }){
            // TODO: Throw or ignore?
            logAndThrow(IllegalStateException("Transaction has uncommitted id" ))
        }

        if (!signedTransaction.isValidTransaction(authority.publicKey as PublicKey)) {
            logger.error { "Ignoring transaction with invalid signature $transactionId" }
        }

        return true
    }

    // TODO - make entity method?
    protected fun validateEntity(entity: Entity) : Entity {
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
        return entity
    }

    @Synchronized
    override fun commitChanges(vararg entities: Entity)
    {
        entities.forEach { validateEntity(it) }

        if(entities.distinctBy { it.entityId }.size != entities.size){
            logAndThrow(RuntimeException("Attempt to commit changes to multiple entities with the same id."))
        }

        if(entities.any { it.authorityId != authority.authorityId }){
            logAndThrow(RuntimeException("Attempt to commit changes not controlled by authority"))
        }

        val uncommittedFacts = entities.flatMap { it.getFacts() }.filter{ it.transactionId == UNCOMMITTED }
        if (uncommittedFacts.isEmpty()) {
            logger.info { "Ignoring update with no novel facts" }
            return
        }

        // TODO: Cleanup - very messy. Probably lock around epoch
        val transactionId = this.transactionId
        if(transactionId == INVALID_TRANSACTION_ID) {
            logAndThrow("Attempt to commit transaction without setting transaction id")
        }

        val nextTransactionId = transactionId.inc()
        persistTransaction(Transaction.fromFacts(nextTransactionId, uncommittedFacts).sign(signator))
        this.transactionId = nextTransactionId
    }
}