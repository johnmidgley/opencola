package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.security.Signator
import io.opencola.security.Signature
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PublicKey
import io.opencola.network.protobuf.Message as Proto

class SignedMessage(val from: Id, val body: UnsignedMessage, val signature: Signature) {
    override fun toString(): String {
        return "SignedMessage(from=$from, message=$body, signature=$signature)"
    }
    constructor(from: Id, message: UnsignedMessage, signator: Signator) : this(
        from,
        message,
        signator.signBytes(from.toString(), message.payload)
    )

    fun encode(): ByteArray {
        return encodeProto(this)
    }

    fun toProto(): Proto.SignedMessage {
        return toProto(this)
    }

    fun hasValidSignature(publicKey: PublicKey): Boolean {
        return signature.isValidSignature(publicKey, body.payload)
    }

    companion object Factory : ProtoSerializable<SignedMessage, Proto.SignedMessage> {
        override fun toProto(value: SignedMessage): Proto.SignedMessage {
            return Proto.SignedMessage.newBuilder()
                .setFrom(Id.toProto(value.from))
                .setUnsignedMessageBytes(UnsignedMessage.toProto(value.body).toByteString())
                .setSignature(Signature.toProto(value.signature))
                .build()
        }

        override fun fromProto(value: Proto.SignedMessage): SignedMessage {
            return SignedMessage(
                Id.fromProto(value.from),
                UnsignedMessage.fromProto(Proto.UnsignedMessage.parseFrom(value.unsignedMessageBytes)),
                Signature.fromProto(value.signature)
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.SignedMessage {
            return Proto.SignedMessage.parseFrom(bytes)
        }
    }
}