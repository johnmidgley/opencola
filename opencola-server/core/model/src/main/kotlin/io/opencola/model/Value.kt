package io.opencola.model

import com.google.protobuf.ByteString
import io.opencola.model.protobuf.Model as ProtoModel
import kotlinx.serialization.Serializable
import io.opencola.serialization.*
import io.opencola.serialization.codecs.BooleanByteArrayCodec
import io.opencola.serialization.codecs.FloatByteArrayCodec
import io.opencola.serialization.codecs.IntByteArrayCodec
import io.opencola.serialization.codecs.LongByteArrayCodec
import io.opencola.serialization.protobuf.ProtoSerializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*

@Serializable
// TODO: Can codec be put here?
data class Value(val bytes: ByteArray, val valueType: ValueType = ValueType.ANY) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Value

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    companion object Factory : StreamSerializer<Value>, ProtoSerializable<Value, ProtoModel.Value> {
        val emptyValue = Value("".toByteArray())

        override fun encode(stream: OutputStream, value: Value) {
            stream.writeByteArray(value.bytes)
        }

        override fun decode(stream: InputStream): Value {
            val bytes = stream.readByteArray()
            return if(bytes.isEmpty()) emptyValue else Value(bytes)
        }

        override fun toProto(value: Value): ProtoModel.Value {
            val builder = ProtoModel.Value.newBuilder()
            builder.ocType = value.valueType.ordinal

            when(value.valueType) {
                ValueType.ANY -> builder.bytes = ByteString.copyFrom(value.bytes)
                ValueType.BOOLEAN -> builder.bool = BooleanByteArrayCodec.decode(value.bytes)
                ValueType.INT -> builder.int32 = IntByteArrayCodec.decode(value.bytes)
                ValueType.LONG -> builder.int64 = LongByteArrayCodec.decode(value.bytes)
                ValueType.FLOAT -> builder.float = FloatByteArrayCodec.decode(value.bytes)
                ValueType.DOUBLE -> throw NotImplementedError("DOUBLE is not supported")
                ValueType.STRING -> builder.string = String(value.bytes)
                ValueType.BYTES -> builder.bytes = ByteString.copyFrom(value.bytes)
                ValueType.DATETIME -> throw NotImplementedError("DATETIME is not supported")
                ValueType.URI -> builder.string = String(value.bytes)
                ValueType.UUID -> builder.bytes = ByteString.copyFrom(value.bytes)
                ValueType.ID -> builder.bytes = ByteString.copyFrom(value.bytes)
            }

            builder.setBytes(ByteString.copyFrom(value.bytes))
            return builder.build()
        }

        override fun fromProto(value: ProtoModel.Value): Value {
            return when (value.ocType) {
                ValueType.ANY.ordinal -> Value(value.bytes.toByteArray(), ValueType.ANY)
//                ValueType.BOOLEAN.ordinal -> Value(BooleanByteArrayCodec.encode(value.bool), ValueType.BOOLEAN)
//                ValueType.INT.ordinal -> Value(IntByteArrayCodec.encode(value.int32), ValueType.INT)
//                ValueType.LONG.ordinal -> Value(LongByteArrayCodec.encode(value.int64), ValueType.LONG)
//                ValueType.FLOAT.ordinal -> Value(FloatByteArrayCodec.encode(value.float), ValueType.FLOAT)
//                ValueType.DOUBLE.ordinal -> throw NotImplementedError("DOUBLE is not supported")
//                ValueType.STRING.ordinal -> Value(value.string.toByteArray(), ValueType.STRING)
//                ValueType.BYTES.ordinal -> Value(value.bytes.toByteArray(), ValueType.BYTES)
//                ValueType.DATETIME.ordinal -> throw NotImplementedError("DATETIME is not supported")
//                ValueType.URI.ordinal -> Value(value.string.toByteArray(), ValueType.URI)
//                ValueType.UUID.ordinal -> Value(value.bytes.toByteArray(), ValueType.UUID)
//                ValueType.ID.ordinal -> Value(value.bytes.toByteArray(), ValueType.ID)
                else -> throw NotImplementedError("Unknown ValueType")
            }
        }
    }
}

class MultiValueListItem(val key: UUID, val bytes: ByteArray) {
    fun toValue(): Value {
        ByteArrayOutputStream().use {
            it.writeUUID(key)
            it.writeByteArray(bytes)
            return Value(it.toByteArray())
        }
    }

    companion object Factory {
        fun keyOf(value: Value): UUID {
            ByteArrayInputStream(value.bytes).use { return it.readUUID() }
        }

        fun fromValue(value: Value): MultiValueListItem {
            ByteArrayInputStream(value.bytes).use {
                return MultiValueListItem(it.readUUID(), it.readByteArray())
            }
        }
    }
}

// TODO: Templatize
class MultiValueListOfStringItem(val key: UUID, val value: String?) {
    constructor(value: String?) : this (UUID.randomUUID(), value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MultiValueListOfStringItem

        if (other.key != key) return false
        if (other.value != value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }

    companion object Factory{
        fun fromMultiValue(multiValue: MultiValueListItem): MultiValueListOfStringItem {
            return MultiValueListOfStringItem(multiValue.key, String(multiValue.bytes))
        }
    }
}

