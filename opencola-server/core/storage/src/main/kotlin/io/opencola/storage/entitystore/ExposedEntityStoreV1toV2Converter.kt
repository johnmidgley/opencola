package io.opencola.storage.entitystore

import io.opencola.model.*
import io.opencola.model.value.emptyValue
import io.opencola.security.Signator
import io.opencola.security.isValidSignature
import io.opencola.serialization.EncodingFormat
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import mu.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.exists

private val logger = KotlinLogging.logger("EntityStoreConverter")

fun cleanFact(fact: TransactionFact) : TransactionFact? {
    return if(fact.operation == Operation.Add && fact.value == emptyValue) null else fact
}

fun migrateTransaction(entityStoreV2: ExposedEntityStoreV2, transaction: Transaction) : Transaction {
    val authorityId = transaction.authorityId
    val nextTransactionId = entityStoreV2.getNextTransactionIdForV2MigrationOnly(authorityId)
    val cleanedEntities = transaction.transactionEntities.map { transactionEntity ->
        val cleanedFacts = transactionEntity.facts.mapNotNull { cleanFact(it) }
        TransactionEntity(transactionEntity.entityId, cleanedFacts)
    }

    return Transaction(nextTransactionId, authorityId, cleanedEntities, transaction.epochSecond)
}

fun convertExposedEntityStoreV1ToV2(
    addressBook: AddressBook,
    signator: Signator,
    name: String,
    v1Path: Path,
    v2Path: Path,
) {
    require(v1Path.exists())
    require(!v2Path.exists())

    logger.info { "Converting $name from V1 to V2" }

    val entityStoreV1 = ExposedEntityStore(name, v1Path, ::getSQLiteDB, signator, addressBook)
    val entityStoreV2 = ExposedEntityStoreV2(name, v2Path, ::getSQLiteDB, Attributes.get(), signator, addressBook)
    val personas = addressBook.getEntries().filterIsInstance<PersonaAddressBookEntry>()
    val personaIds = personas.map { it.entityId }.toSet()
    val numTransactionsV1 = entityStoreV1.getAllSignedTransactions().filter{ it.transaction.authorityId in personaIds }.count()
    var numTransactionsConverted = 0

    personas
        .forEach { persona ->
            val personaAlias = persona.entityId.toString()
            logger.info { "Converting transactions for name:${persona.name} id:${persona.entityId}" }

            entityStoreV1.getAllSignedTransactions(setOf(persona.entityId))
                .forEachIndexed { idx, signedTransaction ->
                    logger.info { "Converting transaction $idx: id=${signedTransaction.transaction.id}" }
                    require(signedTransaction.transaction.authorityId == persona.entityId)
                    require(signedTransaction.encodingFormat == EncodingFormat.OC)
                    val migratedTransaction = migrateTransaction(entityStoreV2, signedTransaction.transaction)
                    val v2TransactionBytes = migratedTransaction.encodeProto()
                    val v2TransactionSignature = signator.signBytes(personaAlias, v2TransactionBytes)
                    val publicKey = addressBook.getPublicKey(persona.entityId)!!
                    require(isValidSignature(publicKey, v2TransactionBytes, v2TransactionSignature))
                    val v2SignedTransaction = SignedTransaction(EncodingFormat.PROTOBUF, v2TransactionBytes, v2TransactionSignature)
                    entityStoreV2.addSignedTransactions(listOf(v2SignedTransaction))
                    numTransactionsConverted++
                }
        }

    val numTransactionsV2 = entityStoreV2.getAllSignedTransactions().filter{ it.transaction.authorityId in personaIds }.count()
    require(numTransactionsConverted == numTransactionsV1)
    require(numTransactionsV1 == numTransactionsV2)
    logger.info { "Converted $numTransactionsConverted transactions" }
}