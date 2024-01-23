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

package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.security.*
import io.opencola.network.protobuf.Network as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import java.security.PrivateKey
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec


class Recipient(val to: Id, val messageSecretKey: SignedBytes) {
    constructor(from: PersonaAddressBookEntry, to: AddressBookEntry, messageSecretKey: SecretKey) :
            this(to.entityId, sign(from.keyPair.private, encrypt(to.publicKey, messageSecretKey.encoded).encodeProto()))

    companion object : ProtoSerializable<Recipient, Proto.Recipient> {
        override fun toProto(value: Recipient): Proto.Recipient {
            return Proto.Recipient.newBuilder()
                .setTo(value.to.toProto())
                .setMessageSecretKey(value.messageSecretKey.toProto())
                .build()
        }

        override fun fromProto(value: Proto.Recipient): Recipient {
            return Recipient(
                to = Id.fromProto(value.to),
                messageSecretKey = SignedBytes.fromProto(value.messageSecretKey)
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.Recipient {
            return Proto.Recipient.parseFrom(bytes)
        }
    }

    override fun toString(): String {
        return "Recipient(to=$to, messageSecretKeyHash=${Id.ofData(messageSecretKey.bytes)})})"
    }

    fun toProto(): Proto.Recipient {
        return toProto(this)
    }

    fun decryptMessageSecretKey(privateKey: PrivateKey): SecretKey {
        return messageSecretKey.bytes
            .let { EncryptedBytes.decodeProto(it) }
            .let { decrypt(privateKey, it) }
            .let { SecretKeySpec(it, 0, it.size, "AES") }
    }
}