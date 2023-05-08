package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.security.Encryptor
import io.opencola.serialization.protobuf.Message as ProtoMessage
import com.google.protobuf.ByteString

class MessageEnvelope(val to: Id, val signedMessage: SignedMessage) {
    fun encode(encryptor: Encryptor? = null) : ByteArray {
        val toAlias = to.toString()
        val encodedSignedBytes = signedMessage.encode()
        val encryption = encryptor?.let { encryptor.encrypt(toAlias, encodedSignedBytes) }
        val messageBytes = encryption?.bytes ?: encodedSignedBytes

        return ProtoMessage.MessageEnvelope.newBuilder()
            .setId(Id.toProto(to))
            .also { builder -> encryption?.let { builder.setEncryptionTransformation(it.transformation) } }
            .setMessage(ByteString.copyFrom(messageBytes))
            .build()
            .toByteArray()
    }

    companion object {
        fun decode(envelopeBytes: ByteArray, encryptor: Encryptor? = null): MessageEnvelope {
            val envelope = ProtoMessage.MessageEnvelope.parseFrom(envelopeBytes)
            require(envelope.encryptionTransformation.isBlank() || encryptor != null)
            val to = Id.fromProto(envelope.id)
            val messageBytes = envelope.message.toByteArray()
            val signedMessageBytes = encryptor?.let {
                val transformation = envelope.encryptionTransformation
                if (transformation != null) {
                    encryptor.decrypt(to.toString(), messageBytes, transformation)
                } else {
                    // If an encryptor is provided, require a transformation for safety (i.e. to prevent accidentally
                    // sending unencrypted messages)
                    throw RuntimeException("Missing encryption transformation")
                }
            } ?: messageBytes

            return MessageEnvelope(to, SignedMessage.fromProto(ProtoMessage.SignedMessage.parseFrom(signedMessageBytes)))
        }
    }
}

