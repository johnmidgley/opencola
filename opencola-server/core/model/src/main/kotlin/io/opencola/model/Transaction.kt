package io.opencola.model

import io.opencola.model.protobuf.Model as ProtoModel
import kotlinx.serialization.Serializable
import io.opencola.security.SIGNATURE_ALGO
import io.opencola.security.Signator
import io.opencola.serialization.*
import java.io.InputStream
import java.io.OutputStream
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

        fun packProto(transaction: Transaction): io.opencola.model.protobuf.Model.Transaction? {
            return ProtoModel.Transaction.newBuilder()
                .setId(Id.packProto(transaction.id))
                .setAuthorityId(Id.packProto(transaction.authorityId))
                .addAllTransactionEntities(transaction.transactionEntities.map { TransactionEntity.packProto(it) })
                .setEpochSecond(transaction.epochSecond)
                .build()

        }

        fun unpackProto(transaction: io.opencola.model.protobuf.Model.Transaction): Transaction {
            return Transaction(
                Id.unpackProto(transaction.id),
                Id.unpackProto(transaction.authorityId),
                transaction.transactionEntitiesList.map { TransactionEntity.unpackProto(it) },
                transaction.epochSecond
            )
        }
    }
}

