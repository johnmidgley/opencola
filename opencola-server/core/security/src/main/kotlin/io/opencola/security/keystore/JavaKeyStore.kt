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

package io.opencola.security.keystore

import io.opencola.security.SecurityProviderDependent
import io.opencola.security.certificate.createCertificate
import io.opencola.security.hash.Hash
import io.opencola.security.hash.Sha256Hash
import mu.KotlinLogging
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyStore as javaKeyStore
import java.security.PublicKey
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

val defaultPasswordHash = Sha256Hash.ofString("password")

// TODO: Add logging
// TODO: Think about an appropriate HSM
// TODO: Investigate secure strings
// TODO: Make abstract keystore that is base for cert store and authority store
// TODO: Make AuthorityKeyStore, that has encrypt and sign methods
class JavaKeyStore(val path: Path, private val passwordHash: Hash) : SecurityProviderDependent(), KeyStore {
    val logger = KotlinLogging.logger(this.javaClass.simpleName)

    // TODO: Move parameters to config
    private var store: javaKeyStore = javaKeyStore.getInstance("PKCS12","BC")
    private var passwordHashCharArray = passwordHash.toHexString().toCharArray()
    private var protectionParameter = javaKeyStore.PasswordProtection(passwordHashCharArray)

    init{
        (if(path.exists()) path.inputStream() else null).let {
            store.load(it, passwordHashCharArray)
        }
    }

    private fun saveStore() {
        path.outputStream().use {
            store.store(it, passwordHashCharArray)
        }
    }

    override fun addKeyPair(alias: String, keyPair: KeyPair){
        store.setKeyEntry(alias, keyPair.private, null, arrayOf(createCertificate(alias, keyPair)))
        saveStore()
    }

    private fun getEntry(alias: String): javaKeyStore.PrivateKeyEntry? {
        return store.getEntry(alias, protectionParameter) as? javaKeyStore.PrivateKeyEntry
    }

    override fun getKeyPair(alias: String): KeyPair? {
        return getEntry(alias)?.let { KeyPair(it.certificate.publicKey, it.privateKey) }
    }

    override fun deleteKeyPair(alias: String) {
        store.deleteEntry(alias)
        saveStore()
    }

    override fun getPublicKey(alias: String): PublicKey? {
        return getEntry(alias)?.certificate?.publicKey
    }

    override fun getAliases(): List<String> {
        return store.aliases().toList()
    }

    override fun changePassword(newPasswordHash: Hash) {
        changePassword(path, passwordHash, newPasswordHash)
    }
}

fun isPasswordValid(keyStorePath: Path, password: String): Boolean {
    try {
        val store = javaKeyStore.getInstance("PKCS12", "BC")
        keyStorePath.inputStream().use {
            store.load(it, password.toCharArray())
        }
    } catch (e: Exception) {
        return false
    }

    return true
}

fun changePassword(keyStorePath: Path, oldPasswordHash: Hash, newPasswordHash: Hash) {
    val store = javaKeyStore.getInstance("PKCS12", "BC")

    keyStorePath.inputStream().use { inputStream ->
        store.load(inputStream, oldPasswordHash.toHexString().toCharArray())

        keyStorePath.outputStream().use {
            store.store(it, newPasswordHash.toHexString().toCharArray())
        }
    }
}