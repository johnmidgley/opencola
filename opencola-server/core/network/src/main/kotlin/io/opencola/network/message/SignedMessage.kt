package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.security.Signator
import io.opencola.security.Signature
import io.opencola.serialization.protobuf.Message
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.protobuf.Message as ProtoMessage

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
        return toProto(this).toByteArray()
    }

    companion object Factory : ProtoSerializable<SignedMessage, ProtoMessage.SignedMessage> {
        override fun toProto(value: SignedMessage): Message.SignedMessage {
            return Message.SignedMessage.newBuilder()
                .setFrom(Id.toProto(value.from))
                .setMessage(UnsignedMessage.toProto(value.body).toByteString())
                .setSignature(Signature.toProto(value.signature))
                .build()
        }

        override fun fromProto(value: Message.SignedMessage): SignedMessage {
            return SignedMessage(
                Id.fromProto(value.from),
                UnsignedMessage.fromProto(Message.UnsignedMessage.parseFrom(value.message)),
                Signature.fromProto(value.signature)
            )
        }
    }
}