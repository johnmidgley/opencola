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

package io.opencola.security.keystore

import io.opencola.security.PublicKeyProvider
import io.opencola.security.hash.Hash
import java.security.KeyPair

interface KeyStore : PublicKeyProvider<String> {
    fun addKeyPair(alias: String, keyPair: KeyPair)
    fun getKeyPair(alias: String) : KeyPair?
    fun deleteKeyPair(alias: String)
    fun getAliases(): List<String>
    fun changePassword(newPasswordHash: Hash)
}