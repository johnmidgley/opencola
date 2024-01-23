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

import io.opencola.serialization.codecs.FloatByteArrayCodec
import io.opencola.model.protobuf.Model as Proto

class FloatValue(value: Float) : Value<Float>(value) {
    companion object : ValueWrapper<Float> {
        override fun encode(value: Float): ByteArray {
            return FloatByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): Float {
            return FloatByteArrayCodec.decode(value)
        }

        override fun toProto(value: Float): Proto.Value {
            return Proto.Value.newBuilder()
                .setFloat(value)
                .build()
        }

        override fun fromProto(value: Proto.Value): Float {
            require(value.dataCase == Proto.Value.DataCase.FLOAT)
            return value.float
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: Float): Value<Float> {
            return FloatValue(value)
        }

        override fun unwrap(value: Value<Float>): Float {
            require(value is FloatValue)
            return value.get()
        }
    }
}