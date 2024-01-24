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

package io.opencola.relay.common.message.v2

import io.opencola.relay.common.protobuf.Relay as Proto

enum class ControlMessageType(val protoType: Proto.ControlMessage.Type) {
    NONE(Proto.ControlMessage.Type.NONE),
    NO_PENDING_MESSAGES(Proto.ControlMessage.Type.NO_PENDING_MESSAGES),
    ADMIN(Proto.ControlMessage.Type.ADMIN);

    companion object {
        private val protoToTypeMap = entries.associateBy { it.protoType }

        fun fromProto(protoType: Proto.ControlMessage.Type): ControlMessageType {
            return protoToTypeMap[protoType] ?: throw IllegalArgumentException("Unknown ControlMessageType: $protoType")
        }
    }
}