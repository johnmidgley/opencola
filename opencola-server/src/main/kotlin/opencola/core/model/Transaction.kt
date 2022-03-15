package opencola.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import opencola.core.security.SIGNATURE_ALGO
import opencola.core.security.Signator
import opencola.core.security.isValidSignature
import opencola.core.serialization.StreamSerializer
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.time.Instant

@Serializable
data class Transaction(val id: Id, val authorityId: Id, val transactionEntities: List<TransactionEntity>, val epochSecond: Long = Instant.EPOCH.epochSecond){
    fun getFacts(): List<Fact> {
        return transactionEntities.flatMap { entity ->
            entity.facts.map { Fact(authorityId, entity.entityId, it.attribute, it.value, it.operation, id) }
        }
    }

    // TODO: This is common to a number of classes. Figure out how to make properly generic
    override fun toString(): String {
        return Json.encodeToString(this)
    }

    fun sign(signator: Signator) : SignedTransaction {
        // This is probably not the right way to serialize. Likely should create a serializer / provider that can be
        // configured to serialize in an appropriate format.
        // TODO: Validate transaction
        return SignedTransaction(this, SIGNATURE_ALGO, signator.signBytes(authorityId, Transaction.encode(this)))
    }

    @Serializable
    data class TransactionFact(val attribute: Attribute, val value: Value, val operation: Operation) {
         companion object Factory : StreamSerializer<TransactionFact> {
            fun fromFact(fact: Fact): TransactionFact {
                return TransactionFact(fact.attribute, fact.value, fact.operation)
            }

             override fun encode(stream: OutputStream, value: TransactionFact) {
                 Attribute.encode(stream, value.attribute)
                 Value.encode(stream, value.value)
                 Operation.encode(stream, value.operation)
             }

             override fun decode(stream: InputStream): TransactionFact {
                 return TransactionFact(Attribute.decode(stream), Value.decode(stream), Operation.decode(stream))
             }
         }
    }

    @Serializable
    data class TransactionEntity(val entityId: Id, val facts: List<TransactionFact>){
        companion object Factory : StreamSerializer<TransactionEntity> {
            override fun encode(stream: OutputStream, value: TransactionEntity) {
                Id.encode(stream, value.entityId)
                Transaction.writeInt(stream, value.facts.size)
                for(fact in value.facts){
                    TransactionFact.encode(stream, fact)
                }
            }

            override fun decode(stream: InputStream): TransactionEntity {
                return TransactionEntity(Id.decode(stream), readInt(stream).downTo(1).map { TransactionFact.decode(stream) } )
            }
        }
    }

    companion object Factory : StreamSerializer<Transaction> {
        fun fromFacts(id: Id, facts: List<Fact>) : Transaction {
            val (authorityId, transactionEntities) = toTransactionEntities(facts)
            return Transaction(id, authorityId, transactionEntities)
        }

        private fun toTransactionEntities(facts: List<Fact>) : Pair<Id, List<TransactionEntity>> {
            val authorities = facts.map { it.authorityId }.distinctBy { it.toString() }

            if (authorities.size != 1) {
                throw IllegalArgumentException("Attempt to construct transaction with facts from multiple authorities: ${authorities.joinToString()}")
            }

            val transactionEntities = facts.groupBy { it.entityId }
                .map{ (entity, facts) ->
                    TransactionEntity(entity, facts.map { TransactionFact.fromFact(it) }.toList())
                }

            return Pair(authorities.first(), transactionEntities)
        }

        override fun encode(stream: OutputStream, value: Transaction) {
            Id.encode(stream, value.id)
            Id.encode(stream, value.authorityId)
            writeInt(stream, value.transactionEntities.size)
            for(entity in value.transactionEntities){
                TransactionEntity.encode(stream, entity)
            }
            writeLong(stream, value.epochSecond)
        }

        override fun decode(stream: InputStream): Transaction {
            return Transaction(Id.decode(stream), Id.decode(stream), readInt(stream).downTo(1).map { TransactionEntity.decode(stream) }, readLong(stream))
        }
    }
}

@Serializable
data class SignedTransaction(val transaction: Transaction, val algorithm: String, val signature: ByteArray) {
    // TODO: Fix signature serialization - right now json array vs. an encoded hex string
    fun isValidTransaction(publicKey: PublicKey): Boolean {
        return isValidSignature(publicKey, transaction.toString().toByteArray(), signature)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedTransaction

        if (transaction != other.transaction) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    companion object Factory : StreamSerializer<SignedTransaction> {
        override fun encode(stream: OutputStream, value: SignedTransaction) {
            Transaction.encode(stream, value.transaction)
            writeByteArray(stream, SIGNATURE_ALGO.toByteArray())
            writeByteArray(stream, value.signature)
        }

        override fun decode(stream: InputStream): SignedTransaction {
            return SignedTransaction(Transaction.decode(stream), String(readByteArray(stream)), readByteArray(stream))
        }

    }
}

