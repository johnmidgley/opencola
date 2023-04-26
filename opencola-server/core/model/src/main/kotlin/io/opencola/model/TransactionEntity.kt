package io.opencola.model

import io.opencola.model.capnp.Model
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readInt
import io.opencola.serialization.writeInt
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

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