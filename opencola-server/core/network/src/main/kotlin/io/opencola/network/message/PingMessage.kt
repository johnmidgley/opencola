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

import io.opencola.network.protobuf.Network as Proto
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.serialization.protobuf.ProtoSerializable

class PingMessage : Message(MessageStorageKey.none) {
    companion object : ProtoSerializable<PingMessage, Proto.PingMessage> {
        private val proto: Proto.PingMessage = Proto.PingMessage.newBuilder().build()
        override fun toProto(value: PingMessage): Proto.PingMessage {
            return proto
        }

        override fun fromProto(value: Proto.PingMessage): PingMessage {
            return PingMessage()
        }

        override fun parseProto(bytes: ByteArray): Proto.PingMessage {
            return proto
        }
    }
}