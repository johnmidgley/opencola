package io.opencola.relay.common.message

import io.opencola.relay.common.protobuf.Relay
import io.opencola.security.PublicKeyProtoCodec
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PublicKey

class IdentityMessage(val publicKey: PublicKey) {
    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<IdentityMessage, Proto.Identity> {
        override fun toProto(value: IdentityMessage): Proto.Identity {
            return Proto.Identity.newBuilder()
                .setPublicKey(PublicKeyProtoCodec.toProto(value.publicKey))
                .build()
        }

        override fun fromProto(value: Proto.Identity): IdentityMessage {
            return IdentityMessage(PublicKeyProtoCodec.fromProto(value.publicKey))
        }

        override fun parseProto(bytes: ByteArray): Relay.Identity {
            return Proto.Identity.parseFrom(bytes)
        }
    }
}