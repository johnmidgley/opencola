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

package io.opencola.serialization.codecs

import io.opencola.util.toByteArray
import io.opencola.serialization.ByteArrayCodec
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.*

object UUIDByteArrayCodecCodec : ByteArrayCodec<UUID> {
    override fun encode(value: UUID): ByteArray {
        return value.toByteArray()
    }

    override fun decode(value: ByteArray): UUID {
        return ByteArrayInputStream(value).use {
            UUID(
                ByteBuffer.wrap(it.readNBytes(Long.SIZE_BYTES)).long,
                ByteBuffer.wrap(it.readNBytes(Long.SIZE_BYTES)).long
            )
        }
    }
}