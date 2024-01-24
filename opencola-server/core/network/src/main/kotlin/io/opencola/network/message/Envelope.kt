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

import io.opencola.network.protobuf.Network as Proto
import io.opencola.security.SignedBytes
import io.opencola.serialization.protobuf.ProtoSerializable

class Envelope(val header: SignedBytes, val message: SignedBytes)  {
    companion object : ProtoSerializable<Envelope, Proto.Envelope> {
        override fun toProto(value: Envelope): Proto.Envelope {
            return Proto.Envelope.newBuilder()
                .setHeader(value.header.toProto())
                .setMessage(value.message.toProto())
                .build()
        }

        override fun fromProto(value: Proto.Envelope): Envelope {
            return Envelope(
                header = SignedBytes.fromProto(value.header),
                message = SignedBytes.fromProto(value.message)
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.Envelope {
            return Proto.Envelope.parseFrom(bytes)
        }
    }

    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }
}