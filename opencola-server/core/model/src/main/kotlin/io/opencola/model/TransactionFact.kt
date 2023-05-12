package io.opencola.model

import io.opencola.model.value.EmptyValue
import io.opencola.model.value.Value
import io.opencola.model.value.emptyValue
import io.opencola.model.value.emptyValueProto
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.protobuf.Model as ProtoModel
import io.opencola.serialization.StreamSerializer
import java.io.InputStream
import java.io.OutputStream

// @Serializable
data class TransactionFact(val attribute: Attribute, val value: Value<Any>, val operation: Operation) {
    companion object Factory :
        StreamSerializer<TransactionFact>,
        ProtoSerializable<TransactionFact, ProtoModel.TransactionFact> {
        fun fromFact(fact: Fact): TransactionFact {
            return TransactionFact(fact.attribute, fact.value, fact.operation)
        }

        override fun encode(stream: OutputStream, value: TransactionFact) {
            Attribute.encode(stream, value.attribute)
            value.attribute.valueWrapper.encode(stream, value.value)
            Operation.encode(stream, value.operation)
        }

        override fun decode(stream: InputStream): TransactionFact {
            val attribute = Attribute.decode(stream)
            val valueWrapper = attribute.valueWrapper
            val value = valueWrapper.decode(stream)
            val operation = Operation.decode(stream)

            return TransactionFact(attribute, value, operation)
        }


        override fun toProto(value: TransactionFact): ProtoModel.TransactionFact {
            return ProtoModel.TransactionFact.newBuilder()
                .setAttribute(Attribute.toProto(value.attribute))
                .setValue(
                    if (value.value is EmptyValue)
                        emptyValueProto
                    else
                        value.attribute.valueWrapper.toProto(value.value.get())
                )
                .setOperation(Operation.toProto(value.operation))
                .build()
        }

        override fun fromProto(value: ProtoModel.TransactionFact): TransactionFact {
            val attribute = Attribute.fromProto(value.attribute)
            val valueWrapper = attribute.valueWrapper
            val wrappedValue =
                if (value.value.ocType == ProtoModel.OCType.EMPTY)
                    emptyValue
                else
                    valueWrapper.wrap(valueWrapper.fromProto(value.value))
            val operation = Operation.fromProto(value.operation)

            return TransactionFact(attribute, wrappedValue, operation)
        }
    }
}