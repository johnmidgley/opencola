package io.opencola.relay.common.message

import io.opencola.model.Id
import io.opencola.relay.common.message.v1.MessageV1
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.*
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

data class Envelope(
    val recipients: List<Recipient>,
    val messageStorageKey: MessageStorageKey?, // TODO: Make optional?
    val message: SignedBytes // Encrypted and then signed bytes here, since the server uses this class, and it doesn't have access to the message.
) {
    constructor(recipient: Recipient, messageStorageKey: MessageStorageKey?, message: SignedBytes)
            : this(listOf(recipient), messageStorageKey, message)

    companion object {
        fun from(
            from: PrivateKey,
            to: List<PublicKey>,
            messageStorageKey: MessageStorageKey?,
            message: Message
        ): Envelope {
            val messageSecretKey = generateAesKey()
            val messageSecretKeyEncoded = messageSecretKey.encoded

            return Envelope(
                to.map { Recipient(it, encrypt(it, messageSecretKeyEncoded)) },
                messageStorageKey,
                message.encryptAndSign(from, messageSecretKey)
            )
        }

        fun from(
            from: PrivateKey,
            to: PublicKey,
            messageStorageKey: MessageStorageKey?,
            message: Message
        ): Envelope {
            return from(from, listOf(to), messageStorageKey, message)
        }
    }

    fun decryptMessage(recipientKeyPair: KeyPair): Message {
        val encryptedBytes = EncryptedBytes.decodeProto(message.bytes)
        if (encryptedBytes.transformation == EncryptionTransformation.ECIES_WITH_AES_CBC) {
            // TODO: Hack to support Message V1 - remove when V1 is removed
            val messageBytes = decrypt(recipientKeyPair.private, encryptedBytes)
            val messageV1 = MessageV1.decode(messageBytes).also { it.validate() }
            return Message(messageV1.header.messageId, messageV1.header.from, messageV1.body)
        }

        val recipientId = Id.ofPublicKey(recipientKeyPair.public)
        val recipient = recipients.find { it.id() == recipientId }
            ?: throw IllegalArgumentException("Recipient $recipientId not found in $recipients")
        val messageSecretKey = recipient.decryptMessageSecretKey(recipientKeyPair.private)
        return Message.decryptAndVerifySignature(messageSecretKey, message)
    }
}
