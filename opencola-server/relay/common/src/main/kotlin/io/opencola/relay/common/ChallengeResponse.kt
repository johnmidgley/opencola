package io.opencola.relay.common

import io.opencola.relay.common.protobuf.Relay
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.security.Signature
import io.opencola.serialization.protobuf.ProtoSerializable

class ChallengeResponse(val signature: Signature) {
    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<ChallengeResponse, Proto.ChallengeResponse> {
        override fun toProto(value: ChallengeResponse): Relay.ChallengeResponse {
            return Relay.ChallengeResponse.newBuilder()
                .setSignature(value.signature.toProto())
                .build()
        }

        override fun fromProto(value: Relay.ChallengeResponse): ChallengeResponse {
            return ChallengeResponse(Signature.fromProto(value.signature))
        }

        override fun parseProto(bytes: ByteArray): Relay.ChallengeResponse {
            return Relay.ChallengeResponse.parseFrom(bytes)
        }
    }
}