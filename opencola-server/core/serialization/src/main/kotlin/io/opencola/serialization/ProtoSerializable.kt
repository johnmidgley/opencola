package io.opencola.serialization

import com.google.protobuf.GeneratedMessageV3

interface ProtoSerializable<T, P : GeneratedMessageV3> {
    fun toProto(value: T): P
    fun fromProto(value: P): T
}