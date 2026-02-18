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

package io.opencola.util

import io.opencola.util.protobuf.Util as Proto
import java.nio.ByteBuffer
import java.util.*

fun UUID.toByteArray(): ByteArray {
    return ByteBuffer.allocate(Long.SIZE_BYTES * 2)
        .putLong(this.mostSignificantBits)
        .putLong(this.leastSignificantBits)
        .array()
}

fun UUID.toProto() : Proto.UUID {
    return Proto.UUID.newBuilder()
        .setMostSignificantBits(this.mostSignificantBits)
        .setLeastSignificantBits(this.leastSignificantBits)
        .build()
}

fun Proto.UUID.toUUID() : UUID {
    return UUID(this.mostSignificantBits, this.leastSignificantBits)
}

fun parseProto(bytes: ByteArray): Proto.UUID {
    return Proto.UUID.parseFrom(bytes)
}

fun decodeProto(bytes: ByteArray): UUID {
    return parseProto(bytes).toUUID()
}