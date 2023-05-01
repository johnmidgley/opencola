package io.opencola.model.value

import io.opencola.model.protobuf.Model as ProtoModel
import io.opencola.serialization.readUri
import io.opencola.serialization.writeUri
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

class UriValue(value: URI) : Value<URI>(value) {
    companion object : ValueWrapper<URI> {
        override fun encode(stream: OutputStream, value: URI) {
            stream.writeUri(value)
        }

        override fun decode(stream: InputStream): URI {
            return stream.readUri()
        }

        override fun toProto(value: URI): ProtoModel.Value {
            return ProtoModel.Value.newBuilder()
                .setOcType(io.opencola.model.ValueType.URI.ordinal)
                .setString(value.toString())
                .build()
        }

        override fun fromProto(value: ProtoModel.Value): URI {
            require(value.ocType == io.opencola.model.ValueType.URI.ordinal)
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

    override fun compareTo(other: Value<URI>): Int {
        if(other !is UriValue) return -1
        return value.compareTo(other.value)
    }
}