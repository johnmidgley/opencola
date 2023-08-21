package io.opencola.relay.common.message.v2

import com.google.protobuf.ByteString
import io.opencola.relay.common.message.Recipient
import io.opencola.security.*
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.SecretKey

class EnvelopeHeader(val recipients: List<Recipient>, val messageStorageKey: MessageStorageKey?) {
    constructor(recipient: Recipient, messageStorageKey: MessageStorageKey?) : this(
        listOf(recipient),
        messageStorageKey
    )

    constructor(to: List<PublicKey>, messageStorageKey: MessageStorageKey, messageSecretKey: SecretKey) : this(
        to.map { Recipient(it, encrypt(it, messageSecretKey.encoded)) },
        messageStorageKey
    )

    override fun toString(): String {
        return "EnvelopeHeaderV2(recipients=$recipients, storageKey=$messageStorageKey)"
    }

    fun signAndEncrypt(from: PrivateKey, to: PublicKey): EncryptedBytes {
        return encrypt(to, sign(from, encodeProto()).encodeProto())
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<EnvelopeHeader, Proto.EnvelopeHeader> {
        override fun toProto(value: EnvelopeHeader): Proto.EnvelopeHeader {
            return Proto.EnvelopeHeader.newBuilder()
                .addAllRecipients(value.recipients.map { it.toProto() })
                .also { if (value.messageStorageKey?.value != null) it.setStorageKey(ByteString.copyFrom(value.messageStorageKey.encoded())) }
                .build()
        }

        override fun fromProto(value: Proto.EnvelopeHeader): EnvelopeHeader {
            return EnvelopeHeader(
                value.recipientsList.map { Recipient.fromProto(it) },
                if (value.storageKey.isEmpty) null else MessageStorageKey.ofEncoded(value.storageKey.toByteArray())
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.EnvelopeHeader {
            return Proto.EnvelopeHeader.parseFrom(bytes)
        }

        fun from(to: PublicKey, messageStorageKey: MessageStorageKey, messageSecretKey: SecretKey): EnvelopeHeader {
            val recipient = Recipient(to, encrypt(to, messageSecretKey.encoded))
            return EnvelopeHeader(listOf(recipient), messageStorageKey)
        }

        fun decryptAndVerifySignature(to: PrivateKey, from: PublicKey, encrypted: EncryptedBytes): EnvelopeHeader {
            val signedBytes = decrypt(to, encrypted)
            val signedBytesDecoded = SignedBytes.decodeProto(signedBytes)

            if (!signedBytesDecoded.validate(from)) {
                throw SecurityException("Signature validation failed")
            }

            return fromProto(Proto.EnvelopeHeader.parseFrom(signedBytesDecoded.bytes))
        }
    }
}