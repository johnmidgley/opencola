package io.opencola.model

import io.opencola.model.capnp.Model
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