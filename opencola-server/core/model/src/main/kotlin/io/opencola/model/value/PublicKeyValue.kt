package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.model.ValueType
import io.opencola.model.protobuf.Model
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import io.opencola.util.compareTo
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

class PublicKeyValue(value: PublicKey) : Value<PublicKey>(value) {
    companion object : ValueWrapper<PublicKey> {
        override fun encode(stream: OutputStream, value: PublicKey) {
            stream.writeByteArray(value.encoded)
        }

        override fun decode(stream: InputStream): PublicKey {
            return publicKeyFromBytes(stream.readByteArray())
        }

        override fun toProto(value: PublicKey): Model.Value {
            return Model.Value.newBuilder()
                .setOcType(ValueType.PUBLIC_KEY.ordinal)
                .setBytes(ByteString.copyFrom(encode(value)))
                .build()
        }

        override fun fromProto(value: Model.Value): PublicKey {
            require(value.ocType == ValueType.PUBLIC_KEY.ordinal)
            return decode(value.bytes.toByteArray())
        }

        override fun wrap(value: PublicKey): Value<PublicKey> {
            return PublicKeyValue(value)
        }

        override fun unwrap(value: Value<PublicKey>): PublicKey {
            require(value is PublicKeyValue)
            return value.get()
        }
    }

    override fun compareTo(other: Value<PublicKey>): Int {
        if(other !is PublicKeyValue) return -1
        return this.value.encoded.compareTo(other.value.encoded)
    }
}