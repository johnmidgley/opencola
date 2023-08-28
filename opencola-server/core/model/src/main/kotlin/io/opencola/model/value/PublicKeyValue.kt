package io.opencola.model.value

import io.opencola.security.PublicKeyByteArrayCodec
import io.opencola.security.toProto
import io.opencola.security.toPublicKey
import java.security.PublicKey
import io.opencola.model.protobuf.Model as Proto

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
                .setPublicKey(value.toProto())
                .build()
        }

        override fun fromProto(value: Proto.Value): PublicKey {
            require(value.dataCase == Proto.Value.DataCase.PUBLICKEY)
            return value.publicKey.toPublicKey()
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