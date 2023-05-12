package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.security.PublicKeyByteArrayCodec
import java.security.PublicKey
import io.opencola.serialization.protobuf.Model as ProtoModel

class PublicKeyValue(value: PublicKey) : Value<PublicKey>(value) {
    companion object : ValueWrapper<PublicKey> {
        override fun encode(value: PublicKey): ByteArray {
            return PublicKeyByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): PublicKey {
            return PublicKeyByteArrayCodec.decode(value)
        }

        override fun toProto(value: PublicKey): ProtoModel.Value {
            return ProtoModel.Value.newBuilder()
                .setOcType(ProtoModel.OCType.PUBLIC_KEY)
                .setBytes(ByteString.copyFrom(encode(value)))
                .build()
        }

        override fun fromProto(value: ProtoModel.Value): PublicKey {
            require(value.ocType == ProtoModel.OCType.PUBLIC_KEY)
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