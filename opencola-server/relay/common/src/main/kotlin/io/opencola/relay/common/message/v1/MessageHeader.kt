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

package io.opencola.relay.common.message.v1

import io.opencola.model.Id
import io.opencola.security.DEFAULT_SIGNATURE_ALGO
import io.opencola.security.Signature
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.codecs.UUIDByteArrayCodecCodec
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import io.opencola.util.toByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.*

class MessageHeader(val messageId: UUID, val from: PublicKey, val signature: Signature) {
    override fun toString(): String {
        return "Header(messageId=$messageId, from=${Id.ofPublicKey(from)})"
    }

    companion object : StreamSerializer<MessageHeader> {
        override fun encode(stream: OutputStream, value: MessageHeader) {
            // This was the original order of parameters, so need to keep it this way for backwards compatibility
            stream.writeByteArray(value.from.encoded)
            stream.writeByteArray(value.messageId.toByteArray())
            stream.writeByteArray(value.signature.bytes)
        }

        override fun decode(stream: InputStream): MessageHeader {
            // This was the original order of parameters, so need to keep it this way for backwards compatibility
            val from = publicKeyFromBytes(stream.readByteArray())
            val messageId = UUIDByteArrayCodecCodec.decode(stream.readByteArray())
            val signature = Signature(DEFAULT_SIGNATURE_ALGO, stream.readByteArray())

            return MessageHeader(messageId, from, signature)
        }
    }
}