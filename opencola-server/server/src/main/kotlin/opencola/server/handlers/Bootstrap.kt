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

package opencola.server.handlers

import io.opencola.application.Application
import io.opencola.application.SSLConfig
import io.opencola.security.hash.Hash
import io.opencola.security.keystore.JavaKeyStore
import io.opencola.security.keystore.defaultPasswordHash
import mu.KotlinLogging
import opencola.server.getSSLCertificateStore
import java.nio.file.Path
import kotlin.io.path.exists

private val logger = KotlinLogging.logger("web/init")

// TODO: These look like they could live elsewhere

fun getCertStorePath(storagePath: Path) : Path {
    return storagePath.resolve("cert/opencola-ssl.pks")
}

fun getAuthorityStorePath(storagePath: Path): Path {
    return storagePath.resolve("keystore.pks")
}

fun validateAuthorityKeyStorePassword(storagePath: Path, passwordHash: Hash): Boolean {
    try {
        val keyStorePath = getAuthorityStorePath(storagePath)
        if(keyStorePath.exists()) {
            JavaKeyStore(keyStorePath, passwordHash)
        }
    } catch (e: Exception) {
        if(passwordHash != defaultPasswordHash)
            logger.error { "Bad keystore password: ${e.message }" }
        return false
    }

    return true
}

// TODO: Move to authority store specific place
fun changeAuthorityKeyStorePassword(storagePath: Path, oldPasswordHash: Hash, newPasswordHash: Hash) {
    JavaKeyStore(getAuthorityStorePath(storagePath), oldPasswordHash).changePassword(newPasswordHash)
}

fun isNewUser(storagePath: Path): Boolean {
    val authorityStorePath = getAuthorityStorePath(storagePath)
    return !authorityStorePath.exists() || validateAuthorityKeyStorePassword(storagePath, defaultPasswordHash)
}

fun bootstrapInit(storagePath: Path, sslConfig: SSLConfig) {
    val authorityStorePath = getAuthorityStorePath(storagePath)
    val certStorePath = getCertStorePath(storagePath)

    if(isNewUser(storagePath)) {
        if(!authorityStorePath.exists())
            Application.getOrCreateRootKeyPair(storagePath, defaultPasswordHash)

        if(!certStorePath.exists())
            getSSLCertificateStore(storagePath, "password", sslConfig)
    }
}
