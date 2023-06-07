package io.opencola.security

import com.google.protobuf.ByteString
import io.opencola.security.protobuf.Security as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PublicKey

object PublicKeyProtoCodec : ProtoSerializable<PublicKey, Proto.PublicKey> {
    override fun toProto(value: PublicKey): Proto.PublicKey {
        return Proto.PublicKey.newBuilder()
            .setEncoded(ByteString.copyFrom(value.encoded))
            .build()
    }

    override fun fromProto(value: Proto.PublicKey): PublicKey {
        return publicKeyFromBytes(value.encoded.toByteArray())
    }

    override fun parseProto(bytes: ByteArray): Proto.PublicKey {
        return Proto.PublicKey.parseFrom(bytes)
    }
}