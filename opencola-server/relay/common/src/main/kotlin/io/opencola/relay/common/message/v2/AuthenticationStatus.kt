package io.opencola.relay.common.message.v2

import io.opencola.relay.common.protobuf.Relay
import io.opencola.relay.common.protobuf.Relay as Proto

enum class AuthenticationStatus {
    AUTHENTICATED,
    FAILED_CHALLENGE;

    fun toProto(): Relay.AuthenticationStatus {
        return toProto(this)
    }

    companion object {
        fun toProto(value: AuthenticationStatus) : Proto.AuthenticationStatus {
            return when(value) {
                AUTHENTICATED -> Proto.AuthenticationStatus.AUTHENTICATED
                FAILED_CHALLENGE -> Proto.AuthenticationStatus.FAILED_CHALLENGE
            }
        }

        fun fromProto(status: Proto.AuthenticationStatus): AuthenticationStatus {
            return when (status) {
                Proto.AuthenticationStatus.AUTHENTICATED -> AUTHENTICATED
                Proto.AuthenticationStatus.FAILED_CHALLENGE -> FAILED_CHALLENGE
                else -> throw IllegalArgumentException("Unknown AuthenticationStatus: $status")
            }
        }
    }
}
