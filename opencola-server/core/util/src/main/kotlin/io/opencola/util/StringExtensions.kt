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

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

fun String.hexStringToByteArray(): ByteArray {
    // TODO: This may be more direct
    // return BigInteger(this, 16).toByteArray()
    return this.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

fun String.tryParseUri() : URI? {
    return try{
        URI(this)
    }catch (e: Exception){
        null
    }
}

fun String?.blankToNull() : String? {
    return if(this.isNullOrBlank()) null else this
}

inline fun <reified T> String.decodeJson() : T {
    return Json.decodeFromString(this)
}
