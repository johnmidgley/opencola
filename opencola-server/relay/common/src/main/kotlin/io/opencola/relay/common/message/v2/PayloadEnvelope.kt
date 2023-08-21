package io.opencola.relay.common.message.v2

import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.protobuf.Relay
import io.opencola.security.EncryptedBytes
import io.opencola.security.generateAesKey
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PrivateKey
import java.security.PublicKey

class PayloadEnvelope(val header: EncryptedBytes, val message: EncryptedBytes) {
    override fun toString(): String {
        return "EnvelopeV2(header=ENCRYPTED, message=ENCRYPTED)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PayloadEnvelope) return false

        if (header != other.header) return false
        return message == other.message
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + message.hashCode()
        return result
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<PayloadEnvelope, Proto.Envelope> {
        override fun toProto(value: PayloadEnvelope): Proto.Envelope {
            return Proto.Envelope.newBuilder()
                .setHeader(value.header.toProto())
                .setMessage(value.message.toProto())
                .build()
        }

        override fun fromProto(value: Proto.Envelope): PayloadEnvelope {
            return PayloadEnvelope(
                EncryptedBytes.fromProto(value.header),
                EncryptedBytes.fromProto(value.message)
            )
        }

        override fun parseProto(bytes: ByteArray): Relay.Envelope {
            return Proto.Envelope.parseFrom(bytes)
        }

        fun encodePayload(
            from: PrivateKey,
            headerTo: PublicKey,
            messageTo: List<PublicKey>,
            messageStorageKey: MessageStorageKey,
            message: Message
        ): ByteArray {
            val messageSecretKey = generateAesKey()
            val encryptedHeader =
                EnvelopeHeader(messageTo, messageStorageKey, messageSecretKey).signAndEncrypt(from, headerTo)
            val encryptedSignedMessage = message.signAndEncrypt(from, messageSecretKey)
            return PayloadEnvelope(encryptedHeader, encryptedSignedMessage).encodeProto()
        }

        fun decodePayload(to: PrivateKey, from: PublicKey, payload: ByteArray): Envelope {
            return PayloadEnvelope.decodeProto(payload)
                .let { EnvelopeHeader.decryptAndVerifySignature(to, from, it.header) }
                .let { Envelope(it.recipients, it.messageStorageKey, PayloadEnvelope.decodeProto(payload).message) }
        }

        // Server uses this to prune down recipients when forwarding to a single recipient
        fun from(from: PrivateKey, to: PublicKey, envelope: Envelope): PayloadEnvelope {
            val recipient = envelope.recipients.single { it.publicKey == to }
            val encryptedHeader = EnvelopeHeader(recipient, envelope.messageStorageKey).signAndEncrypt(from, to)
            return PayloadEnvelope(encryptedHeader, envelope.message)
        }
    }
}