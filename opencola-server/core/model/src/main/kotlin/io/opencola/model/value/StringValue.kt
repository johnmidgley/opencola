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

import io.opencola.serialization.codecs.StringByteArrayCodec
import io.opencola.model.protobuf.Model as Proto

class StringValue(value: String) : Value<String>(value) {
    companion object Wrapper : ValueWrapper<String> {
        override fun encode(value: String): ByteArray {
            return StringByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): String {
            return StringByteArrayCodec.decode(value)
        }

        override fun toProto(value: String): Proto.Value {
            return Proto.Value.newBuilder()
                .setString(value)
                .build()
        }

        override fun fromProto(value: Proto.Value): String {
            require(value.dataCase == Proto.Value.DataCase.STRING)
            return value.string
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: String): Value<String> {
            return StringValue(value)
        }

        override fun unwrap(value: Value<String>): String {
            require(value is StringValue) { "Cannot unwrap ${value::class.simpleName} as StringValue" }
            return value.get()
        }
    }
}