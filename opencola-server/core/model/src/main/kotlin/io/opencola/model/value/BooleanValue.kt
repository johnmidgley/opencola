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

package io.opencola.model.value

import io.opencola.serialization.codecs.BooleanByteArrayCodec
import io.opencola.model.protobuf.Model as Proto

// TODO: Is it possible to templatize these Value classes?
class BooleanValue(value: Boolean) : Value<Boolean>(value) {
    companion object : ValueWrapper<Boolean> {
        override fun encode(value: Boolean): ByteArray {
            return BooleanByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): Boolean {
            return BooleanByteArrayCodec.decode(value)
        }

        override fun toProto(value: Boolean): Proto.Value {
            return Proto.Value.newBuilder()
                .setBoolean(value)
                .build()
        }

        override fun fromProto(value: Proto.Value): Boolean {
            require(value.dataCase == Proto.Value.DataCase.BOOLEAN)
            return value.boolean
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: Boolean): Value<Boolean> {
            return BooleanValue(value)
        }

        override fun unwrap(value: Value<Boolean>): Boolean {
            require(value is BooleanValue)
            return value.get()
        }
    }
}