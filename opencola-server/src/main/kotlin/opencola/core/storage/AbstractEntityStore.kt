package opencola.core.storage

import mu.KotlinLogging
import opencola.core.content.MhtmlPage
import opencola.core.content.TextExtractor
import opencola.core.event.EventBus
import opencola.core.event.Events
import opencola.core.extensions.ifNotNullOrElse
import opencola.core.extensions.nullOrElse
import opencola.core.model.*
import opencola.core.security.Signator
import opencola.core.storage.EntityStore.*
import org.apache.james.mime4j.message.DefaultMessageWriter
import java.io.ByteArrayOutputStream
import java.security.PublicKey

// TODO: Should support multiple authorities
abstract class AbstractEntityStore(
    val authority: Authority,
    val eventBus: EventBus,
    val fileStore: FileStore,
    val textExtractor: TextExtractor,
    val addressBook: AddressBook,
    protected val signator: Signator
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

    private fun logAndThrow(message: String) {
        logAndThrow(RuntimeException(message))
    }

    protected fun isValidTransaction(signedTransaction: SignedTransaction): Boolean {
        // TODO: Move what can be moved to transaction
        val transactionId = signedTransaction.transaction.id

        if (signedTransaction.transaction.authorityId != authority.entityId) {
            logger.warn { "Ignoring transaction $transactionId with unverifiable authority: $authority" }
            return false
        }

        if (!signedTransaction.isValidTransaction(authority.publicKey as PublicKey)) {
            logger.error { "Ignoring transaction with invalid signature $transactionId" }
        }

        return true
    }

    // TODO - make entity method?
    private fun validateEntity(entity: Entity): Entity {
        val authorityIds = entity.getFacts().map { it.authorityId }.distinct()

        if (authorityIds.size != 1) {
            logAndThrow(RuntimeException("Entity{${entity.entityId}} contains facts from multiple authorities $authorityIds }"))
        }

        val authorityId = authorityIds.single()
        if (entity.authorityId != authorityId) {
            logAndThrow(RuntimeException("Entity{${entity.entityId}} with authority ${entity.authorityId} contains facts from wrong authority $authorityId }"))
        }

        val invalidEntityIds = entity.getFacts().filter { it.entityId != entity.entityId }.map { it.entityId }
        if (invalidEntityIds.isNotEmpty()) {
            logAndThrow(RuntimeException("Entity Id:{${entity.entityId}} contains facts not matching its id: $invalidEntityIds"))
        }

        if (entity.getFacts().distinct().size < entity.getFacts().size) {
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

        if (entities.any { it.authorityId != authority.authorityId }) {
            logAndThrow(RuntimeException("Attempt to commit changes not controlled by authority"))
        }

        val uncommittedFacts = entities.flatMap { it.getFacts() }.filter { it.transactionOrdinal == null }
        if (uncommittedFacts.isEmpty()) {
            logger.info { "Ignoring update with no novel facts" }
            return null
        }

        val (signedTransaction, transactionOrdinal) = persistTransaction(authority.authorityId , uncommittedFacts)

        entities.forEach {
            it.commitFacts(signedTransaction.transaction.epochSecond, transactionOrdinal)
        }

        eventBus.sendMessage(Events.NewTransaction.toString(), SignedTransaction.encode(signedTransaction))
        return signedTransaction
    }

    @Synchronized
    private fun persistTransaction(authorityId: Id, facts: List<Fact>) : Pair<SignedTransaction, Long> {
        val nextTransactionId =
            getSignedTransactions(listOf(authorityId), null, TransactionOrder.Descending, 1)
                .firstOrNull()
                .ifNotNullOrElse({ Id.ofData(SignedTransaction.encode(it)) }, { getFirstTransactionId(authorityId) })

        val signedTransaction = Transaction.fromFacts(nextTransactionId, facts).sign(signator)
        val transactionOrdinal = persistTransaction(signedTransaction)

        return Pair(signedTransaction, transactionOrdinal)
    }

    override fun updateResource(mhtmlPage: MhtmlPage, actions: Actions): ResourceEntity {
        // TODO: Add data id to resource entity - when indexing, index body from the dataEntity
        // TODO: Parse description
        // TODO - EntityStore should detect if a duplicate entity is added. Just merge it?
        val writer = DefaultMessageWriter()
        ByteArrayOutputStream().use { outputStream ->
            writer.writeMessage(mhtmlPage.message, outputStream)
            val pageBytes = outputStream.toByteArray()
            val dataId = fileStore.write(pageBytes)
            val mimeType = textExtractor.getType(pageBytes)
            val resourceId = Id.ofUri(mhtmlPage.uri)
            val entity = (getEntity(authority.authorityId, resourceId) ?: ResourceEntity(
                authority.entityId,
                mhtmlPage.uri
            )) as ResourceEntity

            // Add / update fields
            // TODO - Check if setting null writes a retraction when fields are null
            entity.dataId = dataId
            entity.name = mhtmlPage.title
            entity.text = mhtmlPage.text
            entity.description = mhtmlPage.description
            entity.imageUri = mhtmlPage.imageUri

            actions.trust.nullOrElse { entity.trust = it }
            actions.like.nullOrElse { entity.like = it }
            actions.rating.nullOrElse { entity.rating = it }

            val dataEntity = (getEntity(authority.authorityId, dataId) ?: DataEntity(
                authority.entityId,
                dataId,
                mimeType
            ))

            updateEntities(entity, dataEntity)
            return entity
        }
    }

    override fun deleteEntity(authorityId: Id, entityId: Id) {
        getEntity(authorityId, entityId).nullOrElse { entity ->
            val facts = entity.getFacts()
                .groupBy { it.attribute }
                .mapNotNull { (attribute, facts) ->
                    if(facts.maxByOrNull { it.transactionOrdinal!! }!!.operation == Operation.Add) attribute else null }
                .map { Fact(authorityId, entityId, it, Value.emptyValue, Operation.Retract) }

            persistTransaction(authorityId, facts)
        }
    }

    override fun addSignedTransactions(signedTransactions: List<SignedTransaction>) {
        signedTransactions.forEach {
            val transactionAuthorityId = it.transaction.authorityId
            val publicKey = addressBook.getPublicKey(transactionAuthorityId)
                ?: throw IllegalArgumentException("No public key for: $transactionAuthorityId - cannot persist transaction ${it.transaction.id}")

            if (!it.isValidTransaction(publicKey))
                throw IllegalArgumentException("Transaction ${it.transaction.id} failed to validate from $transactionAuthorityId")

            persistTransaction(it)
            eventBus.sendMessage(Events.NewTransaction.toString(), SignedTransaction.encode(it))
        }
    }
}