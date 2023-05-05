package io.opencola.model.value

import io.opencola.model.ValueType
import io.opencola.serialization.codecs.UriByteArrayCodec
import io.opencola.serialization.protobuf.Model as ProtoModel
import java.net.URI

class UriValue(value: URI) : Value<URI>(value) {
    companion object : ValueWrapper<URI> {
        override fun encode(value: URI): ByteArray {
            return UriByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): URI {
            return UriByteArrayCodec.decode(value)
        }

        override fun toProto(value: URI): ProtoModel.Value {
            return ProtoModel.Value.newBuilder()
                .setOcType(ValueType.URI.ordinal)
                .setString(value.toString())
                .build()
        }

        override fun fromProto(value: ProtoModel.Value): URI {
            require(value.ocType == ValueType.URI.ordinal)
            return URI.create(value.string)
        }

        override fun wrap(value: URI): Value<URI> {
            return UriValue(value)
        }

        override fun unwrap(value: Value<URI>): URI {
            require(value is UriValue)
            return value.get()
        }
    }
}