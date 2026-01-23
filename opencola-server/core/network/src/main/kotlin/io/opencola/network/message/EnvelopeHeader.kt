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

package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.network.protobuf.Network as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

// TODO: MessageStorageKey should be moved to some place in core - shouldn't pull from relay
class EnvelopeHeader(val recipients: List<Recipient>, val messageStorageKey: MessageStorageKey) {
    constructor(recipient: Recipient, messageStorageKey: MessageStorageKey) : this(listOf(recipient), messageStorageKey)

    companion object : ProtoSerializable<EnvelopeHeader, Proto.EnvelopeHeader> {
        override fun toProto(value: EnvelopeHeader): Proto.EnvelopeHeader {
            return Proto.EnvelopeHeader.newBuilder()
                .addAllRecipients(value.recipients.map { it.toProto() })
                .also { value.messageStorageKey.encoded()?.let { key -> it.setStorageKey(ByteString.copyFrom(key)) }}
                .build()

        }

        override fun fromProto(value: Proto.EnvelopeHeader): EnvelopeHeader {
            return EnvelopeHeader(
                value.recipientsList.map { Recipient.fromProto(it) },
                if (value.storageKey.isEmpty) MessageStorageKey.none else MessageStorageKey.ofEncoded(value.storageKey.toByteArray())
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.EnvelopeHeader {
            return Proto.EnvelopeHeader.parseFrom(bytes)
        }
    }

    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }
}