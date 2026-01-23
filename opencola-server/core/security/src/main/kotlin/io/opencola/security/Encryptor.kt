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

package io.opencola.security

import io.opencola.security.keystore.KeyStore
import java.security.PublicKey

class Encryptor(private val keystore: KeyStore) {
    private fun getPublicKey(alias: String): PublicKey {
        return keystore.getPublicKey(alias)
            ?: throw RuntimeException("Unable to find public key for alias: $alias")
    }

    fun encrypt(alias: String, bytes: ByteArray, transformation: EncryptionTransformation = DEFAULT_ENCRYPTION_TRANSFORMATION): EncryptedBytes {
        return encrypt(getPublicKey(alias), bytes, transformation)
    }

    fun decrypt(alias: String, encryptedBytes: EncryptedBytes): ByteArray {
        val privateKey = keystore.getKeyPair(alias)?.private
            ?: throw RuntimeException("Unable to find private key for alias: $alias")

        return decrypt(privateKey, encryptedBytes)
    }
}