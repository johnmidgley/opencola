package opencola.core.storage

import mu.KotlinLogging
import opencola.core.model.*
import java.security.PublicKey

private const val INVALID_EPOCH: Long = -1

abstract class EntityStore(val authority: Authority) {
    // TODO: Make logger class?
    private val logger = KotlinLogging.logger {}
    private fun logAndThrow(exception: Exception) {
        logger.error { exception.message }
        throw exception
    }
    private fun logAndThrow(message: String){
        logAndThrow(RuntimeException(message))
    }

    private var epoch: Long = INVALID_EPOCH

    @Synchronized
    protected fun setEpoch(epoch: Long){
        if(this.epoch != INVALID_EPOCH){
            logAndThrow("Attempt to setEpoch that has already been set")
        }

        this.epoch = epoch
    }

    abstract fun getEntity(authority: Authority, entityId: Id): Entity?
    abstract fun persistTransaction(signedTransaction: SignedTransaction)

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
    fun commitChanges(vararg entities: Entity)
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
        val epoch = this.epoch
        if(epoch == INVALID_EPOCH) {
            logAndThrow("Attempt to commit transaction without setting epoch")
        }

        val nextEpoch = epoch.inc()
        persistTransaction(authority.signTransaction(Transaction.fromFacts(nextEpoch, uncommittedFacts)))
        this.epoch = nextEpoch
    }
}