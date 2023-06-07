package io.opencola.relay.common.message

import io.opencola.security.PublicKeyProtoCodec
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PublicKey

class AuthenticationResult(val status: AuthenticationStatus, val publicKey: PublicKey) {
    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<AuthenticationResult, Proto.AuthenticationResult> {
        override fun toProto(value: AuthenticationResult): Proto.AuthenticationResult {
            return Proto.AuthenticationResult.newBuilder()
                .setStatus(value.status.toProto())
                .setPublicKey(PublicKeyProtoCodec.toProto(value.publicKey))
                .build()
        }

        override fun fromProto(value: Proto.AuthenticationResult): AuthenticationResult {
            return AuthenticationResult(
                AuthenticationStatus.fromProto(value.status),
                PublicKeyProtoCodec.fromProto(value.publicKey)
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.AuthenticationResult {
            return Proto.AuthenticationResult.parseFrom(bytes)
        }
    }
}