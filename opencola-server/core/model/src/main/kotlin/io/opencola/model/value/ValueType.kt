package io.opencola.model.value

import io.opencola.serialization.protobuf.ProtoType

// TODO: Move to value?
enum class ValueType(val protoType: ProtoType) {
    ANY(ProtoType.ANY),
    BOOLEAN(ProtoType.BOOL),
    INT(ProtoType.INT32),
    LONG(ProtoType.INT64),
    FLOAT(ProtoType.FLOAT),
    DOUBLE(ProtoType.DOUBLE),
    STRING(ProtoType.STRING),
    BYTES(ProtoType.BYTES),
    DATETIME(ProtoType.STRING),
    URI(ProtoType.STRING),
    UUID(ProtoType.BYTES),
    ID(ProtoType.BYTES),
    PUBLIC_KEY(ProtoType.BYTES),
}