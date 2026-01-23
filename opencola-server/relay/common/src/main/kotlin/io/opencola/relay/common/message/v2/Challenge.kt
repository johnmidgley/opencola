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

import com.google.protobuf.ByteString
import io.opencola.security.SignatureAlgorithm
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

class ChallengeMessage(val algorithm: SignatureAlgorithm, val challenge: ByteArray) {
    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<ChallengeMessage, Proto.Challenge> {
        override fun toProto(value: ChallengeMessage): Proto.Challenge {
            return Proto.Challenge.newBuilder()
                .setAlgorithm(value.algorithm.protoValue)
                .setChallenge(ByteString.copyFrom(value.challenge))
                .build()
        }

        override fun fromProto(value: Proto.Challenge): ChallengeMessage {
            return ChallengeMessage(SignatureAlgorithm.fromProto(value.algorithm), value.challenge.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.Challenge {
            return Proto.Challenge.parseFrom(bytes)
        }
    }
}