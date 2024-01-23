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

package io.opencola.util

import java.io.ByteArrayOutputStream

fun ByteArray.toHexString() : String {
    // TODO: Use a string builder and make lazy / memoized
    return this.fold("") { str, it -> str + "%02x".format(it) }
}

fun ByteArray.append(other: ByteArray) : ByteArray {
    return ByteArrayOutputStream().use {
        it.write(this)
        it.write(other)
        it.toByteArray()
    }
}

fun ByteArray.compareTo(other: ByteArray): Int {
    val minLength = minOf(this.size, other.size)

    for (i in 0 until minLength) {
        val result = this[i].compareTo(other[i])
        if (result != 0) {
            return result
        }
    }

    return this.size.compareTo(other.size)
}