/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.relay.common.message.v2

import io.opencola.relay.common.protobuf.Relay
import io.opencola.relay.common.protobuf.Relay as Proto

enum class AuthenticationStatus {
    AUTHENTICATED,
    FAILED_CHALLENGE,
    NOT_AUTHORIZED;

    fun toProto(): Relay.AuthenticationStatus {
        return toProto(this)
    }

    companion object {
        fun toProto(value: AuthenticationStatus) : Proto.AuthenticationStatus {
            return when(value) {
                AUTHENTICATED -> Proto.AuthenticationStatus.AUTHENTICATED
                FAILED_CHALLENGE -> Proto.AuthenticationStatus.FAILED_CHALLENGE
                NOT_AUTHORIZED -> Proto.AuthenticationStatus.NOT_AUTHORIZED
            }
        }

        fun fromProto(status: Proto.AuthenticationStatus): AuthenticationStatus {
            return when (status) {
                Proto.AuthenticationStatus.AUTHENTICATED -> AUTHENTICATED
                Proto.AuthenticationStatus.FAILED_CHALLENGE -> FAILED_CHALLENGE
                Proto.AuthenticationStatus.NOT_AUTHORIZED -> NOT_AUTHORIZED
                else -> throw IllegalArgumentException("Unknown AuthenticationStatus: $status")
            }
        }
    }
}
