package io.opencola.model

import io.opencola.model.protobuf.Model as ProtoModel
import io.opencola.serialization.*
import io.opencola.serialization.protobuf.ProtoSerializable
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

// @Serializable
data class Transaction(val id: Id,
                       val authorityId: Id,
                       val transactionEntities: List<TransactionEntity>,
                       val epochSecond: Long = Instant.now().epochSecond) {

    init {
        // TODO: Validate transaction
        require(transactionEntities.isNotEmpty()) { "Transaction must have at least one entity" }
    }

    fun getFacts(transactionOrdinal: Long? = null): List<Fact> {
        return transactionEntities.flatMap { entity ->
            entity.facts.map { Fact(authorityId, entity.entityId, it.attribute, it.value, it.operation, epochSecond, transactionOrdinal) }
        }
    }

    companion object Factory :
        StreamSerializer<Transaction>,
        ProtoSerializable<Transaction, ProtoModel.Transaction> {
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

        override fun toProto(value: Transaction): ProtoModel.Transaction {
            return ProtoModel.Transaction.newBuilder()
                .setId(Id.toProto(value.id))
                .setAuthorityId(Id.toProto(value.authorityId))
                .addAllTransactionEntities(value.transactionEntities.map { TransactionEntity.toProto(it) })
                .setEpochSecond(value.epochSecond)
                .build()

        }

        override fun fromProto(value: ProtoModel.Transaction): Transaction {
            return Transaction(
                Id.fromProto(value.id),
                Id.fromProto(value.authorityId),
                value.transactionEntitiesList.map { TransactionEntity.fromProto(it) },
                value.epochSecond
            )
        }

        fun toByteArray(value: Transaction): ByteArray {
            return toProto(value).toByteArray()
        }

        fun fromByteArray(value: ByteArray): Transaction {
            return fromProto(ProtoModel.Transaction.parseFrom(value))
        }
    }
}

