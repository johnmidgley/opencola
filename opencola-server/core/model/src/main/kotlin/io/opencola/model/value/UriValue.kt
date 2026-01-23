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

import io.opencola.serialization.codecs.UriByteArrayCodec
import io.opencola.model.protobuf.Model as Proto
import java.net.URI

class UriValue(value: URI) : Value<URI>(value) {
    companion object : ValueWrapper<URI> {
        override fun encode(value: URI): ByteArray {
            return UriByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): URI {
            return UriByteArrayCodec.decode(value)
        }

        override fun toProto(value: URI): Proto.Value {
            return Proto.Value.newBuilder()
                .setUri(value.toString())
                .build()
        }

        override fun fromProto(value: Proto.Value): URI {
            require(value.dataCase == Proto.Value.DataCase.URI)
            return URI(value.uri)
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: URI): Value<URI> {
            return UriValue(value)
        }

        override fun unwrap(value: Value<URI>): URI {
            require(value is UriValue)
            return value.get()
        }
    }
}