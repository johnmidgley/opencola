package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.security.Encryptor
import io.opencola.network.protobuf.Message as ProtoMessage
import io.opencola.security.EncryptedBytes
import io.opencola.security.PublicKeyProvider
import io.opencola.security.encrypt

class MessageEnvelope(val to: Id, val signedMessage: SignedMessage) {
    fun encode(publicKeyProvider: PublicKeyProvider<Id>?): ByteArray {
        val toAlias = to.toString()
        val encodedSignedBytes = signedMessage.encode()
        val encryptedBytes = publicKeyProvider?.let {
            publicKeyProvider.getPublicKey(to)?.let { encrypt(it, encodedSignedBytes) }
                ?: throw RuntimeException("Unable to find public key for alias: $toAlias")
        }

        return ProtoMessage.MessageEnvelope.newBuilder()
            .setTo(Id.toProto(to))
            .also {
                if (encryptedBytes == null)
                    it.setSignedMessage(signedMessage.toProto())
                else
                    it.setEncryptedSignedMessage(encryptedBytes.toProto())
            }
            .build()
            .toByteArray()
    }

    companion object {
        fun decode(envelopeBytes: ByteArray, encryptor: Encryptor? = null): MessageEnvelope {
            val envelope = ProtoMessage.MessageEnvelope.parseFrom(envelopeBytes)
            require(!(envelope.hasEncryptedSignedMessage() && encryptor == null))
            val to = Id.fromProto(envelope.to)
            val signedMessage =
                if (envelope.hasEncryptedSignedMessage()) {
                    SignedMessage.fromProto(
                        ProtoMessage.SignedMessage.parseFrom(
                            encryptor!!.decrypt(
                                to.toString(),
                                EncryptedBytes.fromProto(envelope.encryptedSignedMessage)
                            )
                        )
                    )
                } else {
                    SignedMessage.fromProto(envelope.signedMessage)
                }

            return MessageEnvelope(to, signedMessage)
        }
    }
}

