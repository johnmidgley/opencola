/*
 * Copyright 2024 OpenCola
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
import io.opencola.security.*
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class Recipient(val publicKey: PublicKey, val messageSecretKey: EncryptedBytes) {
    override fun toString(): String {
        return "Recipient(to=${Id.ofPublicKey(publicKey)})"
    }

    fun id(): Id {
        return Id.ofPublicKey(publicKey)
    }

    fun toProto(): Proto.Recipient {
        return toProto(this)
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    fun decryptMessageSecretKey(privateKey: PrivateKey): SecretKey {
        return decrypt(privateKey, messageSecretKey).let { SecretKeySpec(it, 0, it.size, "AES") }
    }

    companion object : ProtoSerializable<Recipient, Proto.Recipient> {
        override fun toProto(value: Recipient): Proto.Recipient {
            return Proto.Recipient.newBuilder()
                .setTo(value.publicKey.toProto())
                .setMessageSecretKey(value.messageSecretKey.toProto())
                .build()
        }

        override fun fromProto(value: Proto.Recipient): Recipient {
            return Recipient(
                value.to.toPublicKey(),
                value.messageSecretKey.toEncryptedBytes()
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.Recipient {
            return Proto.Recipient.parseFrom(bytes)
        }
    }
}
