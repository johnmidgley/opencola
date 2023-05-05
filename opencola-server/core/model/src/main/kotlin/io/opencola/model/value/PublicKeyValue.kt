package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.model.ValueType.PUBLIC_KEY
import io.opencola.serialization.protobuf.Model
import io.opencola.security.PublicKeyByteArrayCodec
import java.security.PublicKey

class PublicKeyValue(value: PublicKey) : Value<PublicKey>(value) {
    companion object : ValueWrapper<PublicKey> {
        override fun encode(value: PublicKey): ByteArray {
            return PublicKeyByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): PublicKey {
            return PublicKeyByteArrayCodec.decode(value)
        }

        override fun toProto(value: PublicKey): Model.Value {
            return Model.Value.newBuilder()
                .setOcType(PUBLIC_KEY.ordinal)
                .setBytes(ByteString.copyFrom(encode(value)))
                .build()
        }

        override fun fromProto(value: Model.Value): PublicKey {
            require(value.ocType == PUBLIC_KEY.ordinal)
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
}