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
import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.network.protobuf.Network as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

class PutDataMessage(val dataId: Id, val data: ByteArray) : Message(MessageStorageKey.of(dataId)) {
    companion object : ProtoSerializable<PutDataMessage, Proto.PutDataMessage>  {
        override fun toProto(value: PutDataMessage): Proto.PutDataMessage {
            return Proto.PutDataMessage
                .newBuilder()
                .setId(value.dataId.toProto())
                .setData(ByteString.copyFrom(value.data))
                .build()
        }

        override fun fromProto(value: Proto.PutDataMessage): PutDataMessage {
            return PutDataMessage(Id.fromProto(value.id), value.data.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.PutDataMessage {
            return Proto.PutDataMessage.parseFrom(bytes)
        }
    }
}