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

import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.security.Signature
import io.opencola.serialization.protobuf.ProtoSerializable

class ChallengeResponse(val signature: Signature) {
    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<ChallengeResponse, Proto.ChallengeResponse> {
        override fun toProto(value: ChallengeResponse): Proto.ChallengeResponse {
            return Proto.ChallengeResponse.newBuilder()
                .setSignature(value.signature.toProto())
                .build()
        }

        override fun fromProto(value: Proto.ChallengeResponse): ChallengeResponse {
            return ChallengeResponse(Signature.fromProto(value.signature))
        }

        override fun parseProto(bytes: ByteArray): Proto.ChallengeResponse {
            return Proto.ChallengeResponse.parseFrom(bytes)
        }
    }
}