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

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.security.*
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.util.toProto
import io.opencola.util.toUUID
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import javax.crypto.SecretKey

/**
 * Message content that is only visible to the intended recipient.
 */
class Message(val id: UUID, val from: PublicKey, val body: ByteArray) {
    constructor(from: PublicKey, body: ByteArray) : this(UUID.randomUUID(), from, body)

    override fun toString(): String {
        return "Message(id=$id, from=${Id.ofPublicKey(from)}, body=${body.size} bytes)"
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    fun encryptAndSign(from: PrivateKey, messageSecretKey: SecretKey) : SignedBytes {
        return sign(from, encrypt(messageSecretKey, encodeProto()).encodeProto())
    }

    companion object : ProtoSerializable<Message, Proto.Message> {
        override fun toProto(value: Message): Proto.Message {
            return Proto.Message.newBuilder()
                .setId(value.id.toProto())
                .setFrom(value.from.toProto())
                .setBody(ByteString.copyFrom(value.body))
                .build()
        }

        override fun fromProto(value: Proto.Message): Message {
            return Message(
                value.id.toUUID(),
                value.from.toPublicKey(),
                value.body.toByteArray()
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.Message {
            return Proto.Message.parseFrom(bytes)
        }

        fun decryptAndVerifySignature(messageSecretKey: SecretKey, signedMessage: SignedBytes) : Message {
            val decryptedBytes = decrypt(messageSecretKey, EncryptedBytes.decodeProto(signedMessage.bytes))
            val message = decodeProto(decryptedBytes)

            if(!signedMessage.validate(message.from)) {
                throw SecurityException("Message signature is invalid")
            }

            return message
        }
    }
}