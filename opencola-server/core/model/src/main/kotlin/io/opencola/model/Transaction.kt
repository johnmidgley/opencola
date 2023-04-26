package io.opencola.model

import io.opencola.model.capnp.Model
import kotlinx.serialization.Serializable
import io.opencola.security.SIGNATURE_ALGO
import io.opencola.security.Signator
import io.opencola.security.isValidSignature
import io.opencola.serialization.*
import io.opencola.serialization.capnproto.pack as capnprotoPack
import io.opencola.serialization.capnproto.unpack as capnprotoUnpack
import org.capnproto.MessageBuilder
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.time.Instant

@Serializable
data class Transaction(val id: Id,
                       val authorityId: Id,
                       val transactionEntities: List<TransactionEntity>,
                       val epochSecond: Long = Instant.now().epochSecond) {

    fun getFacts(transactionOrdinal: Long? = null): List<Fact> {
        return transactionEntities.flatMap { entity ->
            entity.facts.map { Fact(authorityId, entity.entityId, it.attribute, it.value, it.operation, epochSecond, transactionOrdinal) }
        }
    }

    fun sign(signator: Signator) : SignedTransaction {
        // This is probably not the right way to serialize. Likely should create a serializer / provider that can be
        // configured to serialize in an appropriate format.
        // TODO: Validate transaction
        return SignedTransaction(this, SIGNATURE_ALGO, signator.signBytes(authorityId.toString(), encode(this)))
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

            private fun packOperation(operation: Operation) : Model.Operation {
                return when (operation) {
                    Operation.Add -> Model.Operation.ADD
                    Operation.Retract -> Model.Operation.RETRACT
                }
            }

            private fun unpackOperation(operation: Model.Operation) : Operation {
                return when (operation) {
                    Model.Operation.ADD -> Operation.Add
                    Model.Operation.RETRACT -> Operation.Retract
                    else -> throw IllegalArgumentException("Unknown operation: $operation")
                }
            }

            fun pack(transactionFact: TransactionFact, builder: Model.TransactionFact.Builder) {
                Attribute.pack(transactionFact.attribute, builder.initAttribute())
                Value.pack(transactionFact.value, builder.initValue())
                builder.operation = packOperation(transactionFact.operation)
            }

            fun unpack(reader: Model.TransactionFact.Reader): TransactionFact {
                return TransactionFact(
                    Attribute.unpack(reader.attribute),
                    Value.unpack(reader.value),
                    unpackOperation(reader.operation)
                )
            }
        }
    }

    @Serializable
    data class TransactionEntity(val entityId: Id, val facts: List<TransactionFact>){
        companion object Factory : StreamSerializer<TransactionEntity> {
            override fun encode(stream: OutputStream, value: TransactionEntity) {
                Id.encode(stream, value.entityId)
                stream.writeInt(value.facts.size)
                for(fact in value.facts){
                    TransactionFact.encode(stream, fact)
                }
            }

            override fun decode(stream: InputStream): TransactionEntity {
                return TransactionEntity(Id.decode(stream), stream.readInt().downTo(1).map { TransactionFact.decode(stream) } )
            }

            fun pack(transactionEntity: TransactionEntity, builder: Model.TransactionEntity.Builder) {
                Id.pack(transactionEntity.entityId, builder.initEntityId())
                val facts = builder.initFacts(transactionEntity.facts.size)
                transactionEntity.facts.forEachIndexed { index, transactionFact ->
                    TransactionFact.pack(transactionFact, facts[index])
                }
            }

            fun unpack(reader: Model.TransactionEntity.Reader): TransactionEntity {
                return TransactionEntity(
                    Id.unpack(reader.entityId),
                    reader.facts.map { TransactionFact.unpack(it) }
                )
            }
        }
    }

    companion object Factory : StreamSerializer<Transaction> {
        fun fromFacts(id: Id, facts: List<Fact>, epochSecond: Long = Instant.now().epochSecond) : Transaction {
            val (authorityId, transactionEntities) = toTransactionEntities(facts)
            return Transaction(id, authorityId, transactionEntities, epochSecond)
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
            stream.writeInt(value.transactionEntities.size)
            for(entity in value.transactionEntities){
                TransactionEntity.encode(stream, entity)
            }
            stream.writeLong(value.epochSecond)
        }

        override fun decode(stream: InputStream): Transaction {
            return Transaction(Id.decode(stream), Id.decode(stream), stream.readInt().downTo(1).map { TransactionEntity.decode(stream) }, stream.readLong())
        }

        fun pack(transaction: Transaction, builder: Model.Transaction.Builder) {
            Id.pack(transaction.id, builder.initId())
            Id.pack(transaction.authorityId, builder.initAuthorityId())
            val transactionEntities = builder.initTransactionEntities(transaction.transactionEntities.size)
            transaction.transactionEntities.forEachIndexed { index, transactionEntity ->
                TransactionEntity.pack(transactionEntity, transactionEntities[index])
            }
            builder.epochSecond = transaction.epochSecond
        }

        fun unpack(reader: Model.Transaction.Reader): Transaction {
            return Transaction(
                Id.unpack(reader.id),
                Id.unpack(reader.authorityId),
                reader.transactionEntities.map { TransactionEntity.unpack(it) },
                reader.epochSecond
            )
        }
    }
}

@Serializable
// TODO: Make Signature type that has algorithm and signature value
data class SignedTransaction(val transaction: Transaction, val algorithm: String, val signature: ByteArray) {
    fun isValidTransaction(publicKey: PublicKey): Boolean {
        return isValidSignature(publicKey, Transaction.encode(transaction), signature)
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
            stream.writeByteArray(SIGNATURE_ALGO.toByteArray())
            stream.writeByteArray(value.signature)
        }

        override fun decode(stream: InputStream): SignedTransaction {
            return SignedTransaction(Transaction.decode(stream), String(stream.readByteArray()), stream.readByteArray())
        }

        fun pack(signedTransaction: SignedTransaction): ByteArray {
            val messageBuilder = MessageBuilder()
            val message = messageBuilder.initRoot(Model.SignedTransaction.factory)
            Transaction.pack(signedTransaction.transaction, message.initTransaction())
            val signature = message.initSignature()
            signature.setAlgorithm(signedTransaction.algorithm)
            signature.setBytes(signedTransaction.signature)
            return capnprotoPack(messageBuilder)
        }

        fun unpack(bytes: ByteArray): SignedTransaction {
            val reader = capnprotoUnpack(bytes).getRoot(Model.SignedTransaction.factory)

            return SignedTransaction(
                Transaction.unpack(reader.transaction),
                reader.signature.algorithm.toString(),
                reader.signature.bytes.toArray()
            )
        }
    }
}

