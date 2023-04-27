package io.opencola.model

import io.opencola.model.protobuf.Model as ProtoModel
import io.opencola.serialization.StreamSerializer
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

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


        fun toProto(transactionFact: TransactionFact): ProtoModel.TransactionFact {
            return ProtoModel.TransactionFact.newBuilder()
                .setAttribute(Attribute.toProto(transactionFact.attribute))
                .setValue(Value.toProto(transactionFact.value))
                .setOperation(Operation.toProto(transactionFact.operation))
                .build()
        }

        fun fromProto(transactionFact: ProtoModel.TransactionFact): TransactionFact {
            return TransactionFact(
                Attribute.fromProto(transactionFact.attribute),
                Value.fromProto(transactionFact.value),
                Operation.fromProto(transactionFact.operation)
            )
        }
    }
}