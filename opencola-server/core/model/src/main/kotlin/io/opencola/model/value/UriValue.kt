package io.opencola.model.value

import io.opencola.serialization.codecs.UriByteArrayCodec
import io.opencola.model.protobuf.Model as Proto
import java.net.URI

class UriValue(value: URI) : Value<URI>(value) {
    companion object : ValueWrapper<URI> {
        override fun encode(value: URI): ByteArray {
            return UriByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): URI {
            return UriByteArrayCodec.decode(value)
        }

        override fun toProto(value: URI): Proto.Value {
            return Proto.Value.newBuilder()
                .setUri(value.toString())
                .build()
        }

        override fun fromProto(value: Proto.Value): URI {
            require(value.dataCase == Proto.Value.DataCase.URI)
            return URI(value.uri)
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
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