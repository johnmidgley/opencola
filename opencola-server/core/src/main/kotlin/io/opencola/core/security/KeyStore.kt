package io.opencola.core.security

import mu.KotlinLogging
import io.opencola.core.extensions.toHexString
import io.opencola.core.model.Id
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

// TODO: Add logging
// TODO: Think about an appropriate HSM
// TODO: Investigate secure strings
class KeyStore(val path: Path, password: String) : SecurityProviderDependent() {
    val logger = KotlinLogging.logger(this.javaClass.simpleName)

    // TODO: Move parameters to config
    var store: KeyStore = KeyStore.getInstance("PKCS12","BC")
    private lateinit var passwordHash: CharArray//  = sha256(password).toHexString().toCharArray()
    private lateinit var protectionParameter: KeyStore.PasswordProtection// = KeyStore.PasswordProtection(passwordHash)

    private fun setPassword(password: String) {
        passwordHash = sha256(password).toHexString().toCharArray()
        protectionParameter = KeyStore.PasswordProtection(passwordHash)
    }

    init{
        setPassword(password)
        if(path.exists()){
            path.inputStream().use {
                store.load(it, passwordHash)
            }
        } else
            store.load(null, passwordHash)
    }

    fun addKey(id: Id, keyPair: KeyPair){
        store.setKeyEntry(id.toString(), keyPair.private, null, arrayOf(createCertificate(id, keyPair)))
        path.outputStream().use {
            store.store(it, passwordHash)
        }
    }

    private fun getLegacyKey(id: Id) : KeyStore.Entry? {
        val entry = store.getEntry(id.legacyEncode(), protectionParameter)

        if(entry != null){
            logger.warn { "Updating legacy KeyStore" }
            val privateKeyEntry = entry as KeyStore.PrivateKeyEntry
            addKey(id, KeyPair(entry.certificate.publicKey, entry.privateKey))
            return privateKeyEntry
        }

        return null
    }

    private fun getEntry(id: Id): KeyStore.PrivateKeyEntry {
        val entry = store.getEntry(id.toString(), protectionParameter)  ?: getLegacyKey(id)
            ?: throw RuntimeException("No private key found for $id")

        return entry as KeyStore.PrivateKeyEntry
    }

    fun getPrivateKey(id: Id): PrivateKey? {
        return getEntry(id).privateKey
    }

    fun getPublicKey(id: Id): PublicKey? {
        return getEntry(id).certificate.publicKey
    }

    fun changePassword(newPassword: String) {
        val newPasswordHash = sha256(newPassword).toHexString().toCharArray()
        changePassword(path, String(passwordHash), String(newPasswordHash))
    }

}

fun isPasswordValid(keyStorePath: Path, password: String): Boolean {
    try {
        val store = KeyStore.getInstance("PKCS12", "BC")
        keyStorePath.inputStream().use {
            store.load(it, password.toCharArray())
        }
    } catch (e: Exception) {
        return false
    }

    return true
}

fun changePassword(keyStorePath: Path, oldPassword: String, newPassword: String) {
    val store = KeyStore.getInstance("PKCS12", "BC")

    keyStorePath.inputStream().use { inputStream ->
        store.load(inputStream, oldPassword.toCharArray())

        keyStorePath.outputStream().use {
            store.store(it, newPassword.toCharArray())
        }
    }
}