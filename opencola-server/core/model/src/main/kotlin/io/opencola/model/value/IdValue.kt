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

package io.opencola.model.value

import io.opencola.model.Id
import io.opencola.model.protobuf.Model as Proto

class IdValue(value: Id) : Value<Id>(value) {
    companion object Factory : ValueWrapper<Id> {
        override fun encode(value: Id): ByteArray {
            return Id.encode(value)
        }

        override fun decode(value: ByteArray): Id {
            return Id.decode(value)
        }

        override fun toProto(value: Id): Proto.Value {
            return Proto.Value.newBuilder()
                .setId(Id.toProto(value))
                .build()
        }

        override fun fromProto(value: Proto.Value): Id {
            require(value.dataCase == Proto.Value.DataCase.ID)
            return Id.decodeProto(value.id.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: Id): Value<Id> {
            return IdValue(value)
        }

        override fun unwrap(value: Value<Id>): Id {
            require(value is IdValue)
            return value.get()
        }
    }
}