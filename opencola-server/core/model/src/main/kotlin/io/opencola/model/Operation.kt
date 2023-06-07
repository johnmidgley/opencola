package io.opencola.model

import io.opencola.model.protobuf.Model as Proto
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.codecs.IntByteArrayCodec
import java.io.InputStream
import java.io.OutputStream


enum class Operation {
    Add,
    Retract;

    companion object Factory : StreamSerializer<Operation> {
        override fun encode(stream: OutputStream, value: Operation) {
            stream.write(IntByteArrayCodec.encode(value.ordinal))
        }

        override fun decode(stream: InputStream): Operation {
            val ordinal = IntByteArrayCodec.decode(stream.readNBytes(Int.SIZE_BYTES))

            if(ordinal < 0 || ordinal >= values().size)
                throw IllegalArgumentException("Attempt to decode an Operation with ordinal out of range: $ordinal")

            return values()[ordinal]
        }

        fun toProto(operation: Operation) : Proto.Operation {
            return when (operation) {
                Add -> Proto.Operation.ADD
                Retract -> Proto.Operation.RETRACT
            }
        }

        fun fromProto(operation: Proto.Operation) : Operation {
            return when (operation) {
                Proto.Operation.ADD -> Add
                Proto.Operation.RETRACT -> Retract
                else -> throw IllegalArgumentException("Unknown operation: $operation")
            }
        }
    }
}