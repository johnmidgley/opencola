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

package io.opencola.relay.common.message.v1

import io.opencola.model.Id
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

// This class can be removed after V2 migration and replace by envelopeV2
class PayloadEnvelope(val to: PublicKey, val message: ByteArray) {
    override fun toString(): String {
        return "PayloadEnvelope(to=${Id.ofPublicKey(to)}, message=${message.size} bytes)"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PayloadEnvelope) return false
        if (to != other.to) return false
        return message.contentEquals(other.message)
    }

    override fun hashCode(): Int {
        var result = to.hashCode()
        result = 31 * result + message.contentHashCode()
        return result
    }

    fun encode(): ByteArray {
        return encode(this)
    }

    companion object : StreamSerializer<PayloadEnvelope> {

        // V1 encoding does not include the key
        override fun encode(stream: OutputStream, value: PayloadEnvelope) {
            stream.writeByteArray(value.to.encoded)
            stream.writeByteArray(value.message)
        }

        // V1 encoding does not include the key
        override fun decode(stream: InputStream): PayloadEnvelope {
            return PayloadEnvelope(
                publicKeyFromBytes(stream.readByteArray()),
                stream.readByteArray()
            )
        }
    }
}