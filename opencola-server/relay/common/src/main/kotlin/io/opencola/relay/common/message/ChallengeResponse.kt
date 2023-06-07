package io.opencola.relay.common.message

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