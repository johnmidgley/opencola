package io.opencola.model

import io.opencola.model.value.EmptyValue
import io.opencola.model.value.Value
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.model.protobuf.Model as Proto
import io.opencola.serialization.StreamSerializer
import java.io.InputStream
import java.io.OutputStream

data class TransactionFact(val attribute: Attribute, val value: Value<Any>, val operation: Operation) {
    fun toProto() = Factory.toProto(this)

    companion object Factory :
        StreamSerializer<TransactionFact>,
        ProtoSerializable<TransactionFact?, Proto.TransactionFact> {
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

        override fun toProto(value: TransactionFact?): Proto.TransactionFact {
            require(value != null)
            return Proto.TransactionFact.newBuilder()
                .setAttribute(Attribute.toProto(value.attribute))
                .also {
                    if (value.value !is EmptyValue)
                        it.setValue(value.attribute.valueWrapper.toProto(value.value.get()))
                    else
                        it.setValue(EmptyValue.proto)
                }
                .setOperation(Operation.toProto(value.operation))
                .build()
        }

        override fun fromProto(value: Proto.TransactionFact): TransactionFact? {
            val attribute = Attribute.fromProto(value.attribute) ?: return null

            val valueWrapper = attribute.valueWrapper
            if(value.value.dataCase == Proto.Value.DataCase.DATA_NOT_SET)
                throw IllegalArgumentException("TransactionFact value is not set")

            val wrappedValue =
                if (value.value.dataCase == Proto.Value.DataCase.EMPTY)
                    EmptyValue
                else
                    valueWrapper.wrap(valueWrapper.fromProto(value.value))
            val operation = Operation.fromProto(value.operation)

            return TransactionFact(attribute, wrappedValue, operation)
        }

        override fun parseProto(bytes: ByteArray): Proto.TransactionFact {
            return Proto.TransactionFact.parseFrom(bytes)
        }
    }
}