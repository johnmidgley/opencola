package io.opencola.model

import io.opencola.serialization.ProtoSerializable
import io.opencola.model.protobuf.Model as ProtoModel
import io.opencola.serialization.StreamSerializer
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class TransactionFact(val attribute: Attribute, val value: Value, val operation: Operation) {
    companion object Factory :
        StreamSerializer<TransactionFact>,
        ProtoSerializable<TransactionFact, ProtoModel.TransactionFact> {
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


        override fun toProto(value: TransactionFact): ProtoModel.TransactionFact {
            return ProtoModel.TransactionFact.newBuilder()
                .setAttribute(Attribute.toProto(value.attribute))
                .setValue(Value.toProto(value.value))
                .setOperation(Operation.toProto(value.operation))
                .build()
        }

        override fun fromProto(value: ProtoModel.TransactionFact): TransactionFact {
            return TransactionFact(
                Attribute.fromProto(value.attribute),
                Value.fromProto(value.value),
                Operation.fromProto(value.operation)
            )
        }
    }
}