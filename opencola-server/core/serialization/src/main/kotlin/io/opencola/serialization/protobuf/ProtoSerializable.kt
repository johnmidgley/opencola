package io.opencola.serialization.protobuf

import com.google.protobuf.GeneratedMessageV3

interface ProtoSerializable<T, P : GeneratedMessageV3> {
    fun toProto(value: T): P
    fun fromProto(value: P): T
    fun parseProto(bytes: ByteArray): P

    fun encodeProto(value: T) : ByteArray {
        return toProto(value).toByteArray()
    }

    fun decodeProto(bytes: ByteArray) : T {
        return fromProto(parseProto(bytes))
    }
}