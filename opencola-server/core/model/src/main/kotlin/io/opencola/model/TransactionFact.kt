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

        private fun packOperationProto(operation: Operation) : ProtoModel.Operation {
            return when (operation) {
                Operation.Add -> ProtoModel.Operation.ADD
                Operation.Retract -> ProtoModel.Operation.RETRACT
            }
        }

        fun unpackOperationProto(operation: ProtoModel.Operation) : Operation {
            return when (operation) {
                ProtoModel.Operation.ADD -> Operation.Add
                ProtoModel.Operation.RETRACT -> Operation.Retract
                else -> throw IllegalArgumentException("Unknown operation: $operation")
            }
        }

        fun packProto(transactionFact: TransactionFact): ProtoModel.TransactionFact {
            return ProtoModel.TransactionFact.newBuilder()
                .setAttribute(Attribute.packProto(transactionFact.attribute))
                .setValue(Value.packProto(transactionFact.value))
                .setOperation(packOperationProto(transactionFact.operation))
                .build()
        }

        fun unpackProto(transactionFact: ProtoModel.TransactionFact): TransactionFact {
            return TransactionFact(
                Attribute.unpackProto(transactionFact.attribute),
                Value.unpackProto(transactionFact.value),
                unpackOperationProto(transactionFact.operation)
            )
        }
    }
}