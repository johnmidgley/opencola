package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.security.PublicKeyByteArrayCodec
import java.security.PublicKey
import io.opencola.serialization.protobuf.Model as Proto

class PublicKeyValue(value: PublicKey) : Value<PublicKey>(value) {
    companion object : ValueWrapper<PublicKey> {
        override fun encode(value: PublicKey): ByteArray {
            return PublicKeyByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): PublicKey {
            return PublicKeyByteArrayCodec.decode(value)
        }

        override fun toProto(value: PublicKey): Proto.Value {
            return Proto.Value.newBuilder()
                .setOcType(Proto.OCType.PUBLIC_KEY)
                .setBytes(ByteString.copyFrom(encode(value)))
                .build()
        }

        override fun fromProto(value: Proto.Value): PublicKey {
            require(value.ocType == Proto.OCType.PUBLIC_KEY)
            return decode(value.bytes.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: PublicKey): Value<PublicKey> {
            return PublicKeyValue(value)
        }

        override fun unwrap(value: Value<PublicKey>): PublicKey {
            require(value is PublicKeyValue)
            return value.get()
        }
    }
}