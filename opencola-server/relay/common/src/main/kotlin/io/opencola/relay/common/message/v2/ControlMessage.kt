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

package io.opencola.relay.common.message.v2

import com.google.protobuf.ByteString
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.relay.common.protobuf.Relay as Proto

private val emptyByteArray = ByteArray(0)

class ControlMessage(val type: ControlMessageType, val payload: ByteArray = emptyByteArray) {
    companion object : ProtoSerializable<ControlMessage, Proto.ControlMessage> {
        override fun toProto(value: ControlMessage): Proto.ControlMessage {
            return Proto.ControlMessage.newBuilder()
                .setType(value.type.protoType)
                .also { if (value.payload.isNotEmpty()) it.setPayload(ByteString.copyFrom(value.payload)) }
                .build()
        }

        override fun fromProto(value: Proto.ControlMessage): ControlMessage {
            return ControlMessage(
                ControlMessageType.fromProto(value.type),
                if (value.hasPayload()) value.payload.toByteArray() else emptyByteArray
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.ControlMessage {
            return Proto.ControlMessage.parseFrom(bytes)
        }
    }

    fun toProto(): Proto.ControlMessage {
        return toProto(this)
    }

    fun encodeProto(): ByteArray {
        return toProto().toByteArray()
    }
}