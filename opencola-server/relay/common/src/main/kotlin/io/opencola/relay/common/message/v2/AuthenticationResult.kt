package io.opencola.relay.common.message.v2

import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

class AuthenticationResult(val status: AuthenticationStatus) {
    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<AuthenticationResult, Proto.AuthenticationResult> {
        override fun toProto(value: AuthenticationResult): Proto.AuthenticationResult {
            return Proto.AuthenticationResult.newBuilder()
                .setStatus(value.status.toProto())
                .build()
        }

        override fun fromProto(value: Proto.AuthenticationResult): AuthenticationResult {
            return AuthenticationResult(AuthenticationStatus.fromProto(value.status))
        }

        override fun parseProto(bytes: ByteArray): Proto.AuthenticationResult {
            return Proto.AuthenticationResult.parseFrom(bytes)
        }
    }
}