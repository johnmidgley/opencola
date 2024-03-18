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

import io.opencola.security.Signature
import io.opencola.security.isValidSignature
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPair
import java.util.*

/**
 * Legacy Message (no longer used)
 */
class MessageV1(val header: MessageHeader, val body: ByteArray) {
    constructor(senderKeyPair: KeyPair, body: ByteArray)
            : this (MessageHeader(UUID.randomUUID(), senderKeyPair.public, Signature.of(senderKeyPair.private, body)), body)

    override fun toString(): String {
        return "Message(header=$header, body=${body.size} bytes)"
    }

    fun encode(): ByteArray {
        return encode(this)
    }

    fun validate(): MessageV1 {
        if(!isValidSignature(header.from, body, header.signature.bytes)){
            throw RuntimeException("Invalid Signature")
        }

        return this
    }

    companion object : StreamSerializer<MessageV1> {
        override fun encode(stream: OutputStream, value: MessageV1) {
            MessageHeader.encode(stream, value.header)
            stream.writeByteArray(value.body)
        }

        override fun decode(stream: InputStream): MessageV1 {
            return MessageV1(
                MessageHeader.decode(stream),
                stream.readByteArray())
        }
    }
}