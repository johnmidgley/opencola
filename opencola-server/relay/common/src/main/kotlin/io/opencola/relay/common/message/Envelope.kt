/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.relay.common.message

import io.opencola.model.Id
import io.opencola.relay.common.message.v1.MessageV1
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.*
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Message envelope used as payload when interacting with the relay server.
 */
data class Envelope(
    val recipients: List<Recipient>,
    val messageStorageKey: MessageStorageKey?,
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
