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

import io.opencola.security.PublicKeyByteArrayCodec
import io.opencola.security.toProto
import io.opencola.security.toPublicKey
import java.security.PublicKey
import io.opencola.model.protobuf.Model as Proto

class PublicKeyValue(value: PublicKey) : Value<PublicKey>(value) {
    companion object : ValueWrapper<PublicKey> {
        override fun encode(value: PublicKey): ByteArray {
            return PublicKeyByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): PublicKey {
            return PublicKeyByteArrayCodec.decode(value)
        }

        override fun toProto(value: PublicKey): Proto.Value {
            return Proto.Value.newBuilder()
                .setPublicKey(value.toProto())
                .build()
        }

        override fun fromProto(value: Proto.Value): PublicKey {
            require(value.dataCase == Proto.Value.DataCase.PUBLICKEY)
            return value.publicKey.toPublicKey()
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: PublicKey): Value<PublicKey> {
            return PublicKeyValue(value)
        }

        override fun unwrap(value: Value<PublicKey>): PublicKey {
            require(value is PublicKeyValue)
            return value.get()
        }
    }
}