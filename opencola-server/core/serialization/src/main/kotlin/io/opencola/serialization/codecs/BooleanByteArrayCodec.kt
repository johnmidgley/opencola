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

object BooleanByteArrayCodec : ByteArrayCodec<Boolean> {
    // TODO: Can these be bytes instead?
    private const val FALSE = 0
    private const val TRUE = 1

    override fun encode(value: Boolean): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(if (value) TRUE else FALSE).array()
    }

    override fun decode(value: ByteArray): Boolean {
        return ByteBuffer.wrap(value).int == TRUE
    }
}

// TODO: After DB migration, if ByteArrayCodecs are still used, switch to this one.
//  Legacy DBs have full int valued booleans, so can't change now.

//object BooleanByteArrayCodec : ByteArrayCodec<Boolean> {
//    private val FALSE = toByteArray(0.toByte())
//    private val TRUE = toByteArray(1.toByte())
//
//    fun toByteArray(value: Byte): ByteArray {
//        return ByteBuffer.allocate(1).put(0, value).array()
//    }
//
//    override fun encode(value: Boolean): ByteArray {
//        return if (value) TRUE else FALSE
//    }
//
//    override fun decode(value: ByteArray): Boolean {
//        require(value.size == 1)
//        return value[0] == TRUE[0]
//    }
//}