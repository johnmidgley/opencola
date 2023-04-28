package io.opencola.model

import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.model.protobuf.Model as ProtoModel
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readInt
import io.opencola.serialization.writeInt
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class TransactionEntity(val entityId: Id, val facts: List<TransactionFact>) {
    companion object Factory :
        StreamSerializer<TransactionEntity>,
        ProtoSerializable<TransactionEntity, ProtoModel.TransactionEntity> {
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

        override fun toProto(value: TransactionEntity): ProtoModel.TransactionEntity {
            return ProtoModel.TransactionEntity.newBuilder()
                .setEntityId(Id.toProto(value.entityId))
                .addAllFacts(value.facts.map { TransactionFact.toProto(it) })
                .build()
        }

        override fun fromProto(value: ProtoModel.TransactionEntity): TransactionEntity {
            return TransactionEntity(
                Id.fromProto(value.entityId),
                value.factsList.map { TransactionFact.fromProto(it) }
            )
        }
    }
}