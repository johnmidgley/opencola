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

import io.opencola.security.hash.Hash
import io.opencola.security.keystore.KeyStore
import java.security.KeyPair
import java.security.PublicKey

class MockKeyStore : KeyStore {
    private val keyPairs = mutableMapOf<String, KeyPair>()

    override fun addKeyPair(alias: String, keyPair: KeyPair) {
        keyPairs[alias] = keyPair
    }

    override fun getKeyPair(alias: String): KeyPair? {
        return keyPairs[alias]
    }

    override fun deleteKeyPair(alias: String) {
        keyPairs.remove(alias)
    }

    override fun getAliases(): List<String> {
        return keyPairs.keys.toList()
    }

    override fun changePassword(newPasswordHash: Hash) {
        return
    }

    override fun getPublicKey(alias: String): PublicKey? {
        return keyPairs[alias]?.public
    }
}