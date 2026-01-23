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

package io.opencola.security.hash

import io.opencola.util.hexStringToByteArray
import java.security.MessageDigest

class Sha256Hash private constructor(bytes: ByteArray) : Hash(bytes) {
    companion object {
        fun fromBytes(bytes: ByteArray): Sha256Hash {
            require(bytes.size == 32)
            return Sha256Hash(bytes)
        }

        fun fromHexString(hashHexString: String): Sha256Hash {
            return hashHexString
                .hexStringToByteArray()
                .also { require(it.size == 32) }
                .let { Sha256Hash(it) }
        }

        fun ofBytes(bytes: ByteArray): Sha256Hash {
            return Sha256Hash(
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(bytes)
            )
        }

        fun ofString(input: String): Sha256Hash {
            return ofBytes(input.toByteArray())
        }
    }
}