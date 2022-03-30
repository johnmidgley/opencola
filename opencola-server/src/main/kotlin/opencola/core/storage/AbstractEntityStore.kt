package opencola.core.storage

import mu.KotlinLogging
import opencola.core.event.EventBus
import opencola.core.extensions.ifNotNullOrElse
import opencola.core.model.*
import opencola.core.security.Signator
import opencola.core.storage.EntityStore.*
import java.security.PublicKey

// TODO: Should support multiple authorities
abstract class AbstractEntityStore(val authority: Authority, val eventBus: EventBus,  val addressBook: AddressBook, protected val signator: Signator) : EntityStore {
    // TODO: Assumes transaction has been validated. Cleanup?
    protected abstract fun persistTransaction(signedTransaction: SignedTransaction) : SignedTransaction

    private fun getFirstTransactionId(authorityId: Id): Id {
        return Id.ofData("$authorityId.firstTransaction".toByteArray())
    }

    private fun getNextTransactionId(authorityId: Id): Id{
        return getSignedTransactions(listOf(authorityId), null, TransactionOrder.Descending, 1)
            .firstOrNull()
            .ifNotNullOrElse({ Id.ofData(SignedTransaction.encode(it)) }, { getFirstTransactionId(authorityId) })
    }

    // TODO: Make logger class?
    protected val logger = KotlinLogging.logger("EntityStore")
    protected fun logAndThrow(exception: Exception) {
        logger.error { exception.message }
        throw exception
    }

    private fun logAndThrow(message: String){
        logAndThrow(RuntimeException(message))
    }

    protected fun isValidTransaction(signedTransaction: SignedTransaction): Boolean {
        // TODO: Move what can be moved to transaction
        val transactionId = signedTransaction.transaction.id

        if (signedTransaction.transaction.authorityId != authority.entityId) {
            logger.warn { "Ignoring transaction $transactionId with unverifiable authority: $authority" }
            return false
        }

        if(signedTransaction.transaction.getFacts().any { it.transactionId == null }){
            // TODO: Throw or ignore?
            logAndThrow(IllegalStateException("Transaction has uncommitted id" ))
        }

        if (!signedTransaction.isValidTransaction(authority.publicKey as PublicKey)) {
            logger.error { "Ignoring transaction with invalid signature $transactionId" }
        }

        return true
    }

    // TODO - make entity method?
    private fun validateEntity(entity: Entity) : Entity {
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

    // TODO: This is messed up. Untangle the transaction id usage.
    @Synchronized
    override fun updateEntities(vararg entities: Entity): SignedTransaction? {
        entities.forEach { validateEntity(it) }

        if(entities.distinctBy { it.entityId }.size != entities.size){
            logAndThrow(RuntimeException("Attempt to commit changes to multiple entities with the same id."))
        }

        if(entities.any { it.authorityId != authority.authorityId }){
            logAndThrow(RuntimeException("Attempt to commit changes not controlled by authority"))
        }

        val uncommittedFacts = entities.flatMap { it.getFacts() }.filter{ it.transactionId == null }
        if (uncommittedFacts.isEmpty()) {
            logger.info { "Ignoring update with no novel facts" }
            return null
        }

        val signedTransaction = Transaction.fromFacts(getNextTransactionId(authority.authorityId), uncommittedFacts).sign(signator)
        persistTransaction(signedTransaction)

        entities.forEach {
            it.commitFacts(signedTransaction.transaction.epochSecond, signedTransaction.transaction.id)
        }

        return signedTransaction
    }

    override fun addSignedTransactions(signedTransactions: List<SignedTransaction>) {
        signedTransactions.forEach {
            val transactionAuthorityId = it.transaction.authorityId
            val publicKey = addressBook.getPublicKey(transactionAuthorityId)
                ?: throw IllegalArgumentException("No public key for: $transactionAuthorityId - cannot persist transaction ${it.transaction.id}")

            if (!it.isValidTransaction(publicKey))
                throw IllegalArgumentException("Transaction ${it.transaction.id} failed to validate from $transactionAuthorityId")

            persistTransaction(it)
        }
    }
}