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

package io.opencola.serialization.codecs

import io.opencola.serialization.ByteArrayCodec
import java.nio.ByteBuffer

object LongByteArrayCodec : ByteArrayCodec<Long> {
    override fun encode(value: Long): ByteArray {
        return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()
    }

    override fun decode(value: ByteArray): Long {
        return ByteBuffer.wrap(value).long
    }
}