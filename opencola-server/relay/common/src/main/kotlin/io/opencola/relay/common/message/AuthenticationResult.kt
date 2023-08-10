package io.opencola.relay.common.message

import io.opencola.security.EncryptedBytes
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

class AuthenticationResult(val status: AuthenticationStatus, val sessionKey: EncryptedBytes?) {
    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<AuthenticationResult, Proto.AuthenticationResult> {
        override fun toProto(value: AuthenticationResult): Proto.AuthenticationResult {
            return Proto.AuthenticationResult.newBuilder()
                .setStatus(value.status.toProto())
                .also { if(value.sessionKey != null) it.setSessionKey(value.sessionKey.toProto()) }
                .build()
        }

        override fun fromProto(value: Proto.AuthenticationResult): AuthenticationResult {
            return AuthenticationResult(
                AuthenticationStatus.fromProto(value.status),
                if(value.hasSessionKey()) EncryptedBytes.fromProto(value.sessionKey) else null
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.AuthenticationResult {
            return Proto.AuthenticationResult.parseFrom(bytes)
        }
    }
}