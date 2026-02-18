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

package io.opencola.cli

import io.opencola.security.hash.Hash
import io.opencola.security.hash.Sha256Hash
import io.opencola.security.keystore.JavaKeyStore
import io.opencola.util.toBase58
import java.nio.file.Path

fun keystore(storagePath: Path, keyStoreCommand: KeyStoreCommand, getPasswordHash: () -> Hash) {
    val keyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), getPasswordHash())
    if (keyStoreCommand.list == true) {
        keyStore.getAliases().forEach {
            keyStore.getKeyPair(it)?.let { keyPair ->
                println("alias=$it, public-key=${keyPair.public.encoded.toBase58()}, private-kKey=${keyPair.private.encoded.toBase58()}")
            }
        }
    } else if (keyStoreCommand.change != null) {
        // TODO: New password should be read with password prompt
        keyStore.changePassword(Sha256Hash.ofString(keyStoreCommand.change!!))
    }
}