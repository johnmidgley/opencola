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

package io.opencola.serialization

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

//TODO: When serialization is stable, migrate to custom format serialization
interface StreamSerializer<T> {
    // TODO: Should these just call the byte array codecs and wrap them with size markers?
    fun encode(stream: OutputStream, value: T)
    fun decode(stream: InputStream): T

    // TODO: This doesn't feel right here - as it's not for streams. Maybe this should just be Serializer?
    fun encode(value: T) : ByteArray {
        return ByteArrayOutputStream().use {
            encode(it, value)
            it.toByteArray()
        }
    }

    fun decode(value: ByteArray) : T {
        return ByteArrayInputStream(value).use {
            decode(it)
        }
    }
}