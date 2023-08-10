package io.opencola.relay.common.message

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