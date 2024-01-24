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

import io.opencola.serialization.codecs.IntByteArrayCodec
import io.opencola.model.protobuf.Model as Proto

class IntValue(value: Int) : Value<Int>(value) {
    companion object : ValueWrapper<Int> {
        override fun encode(value: Int): ByteArray {
            return IntByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): Int {
            return IntByteArrayCodec.decode(value)
        }

        override fun toProto(value: Int): Proto.Value {
            return Proto.Value.newBuilder()
                .setInt(value)
                .build()
        }

        override fun fromProto(value: Proto.Value): Int {
            require(value.dataCase == Proto.Value.DataCase.INT)
            return value.int
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: Int): Value<Int> {
            return IntValue(value)
        }

        override fun unwrap(value: Value<Int>): Int {
            require(value is IntValue)
            return value.get()
        }
    }
}