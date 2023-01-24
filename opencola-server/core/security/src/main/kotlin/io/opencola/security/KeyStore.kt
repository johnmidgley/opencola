package io.opencola.security

import mu.KotlinLogging
import io.opencola.util.toHexString
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
// TODO: Make abstract keystore that is base for cert store and authority store
// TODO: Make AuthorityKeyStore, that has encrypt and sign methods
class KeyStore(val path: Path, password: String) : SecurityProviderDependent() {
    val logger = KotlinLogging.logger(this.javaClass.simpleName)

    // TODO: Move parameters to config
    private var store: KeyStore = KeyStore.getInstance("PKCS12","BC")
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

    fun addKey(alias: String, keyPair: KeyPair){
        store.setKeyEntry(alias, keyPair.private, null, arrayOf(createCertificate(alias, keyPair)))
        path.outputStream().use {
            store.store(it, passwordHash)
        }
    }

    private fun getEntry(alias: String): KeyStore.PrivateKeyEntry? {
        return store.getEntry(alias, protectionParameter) as? KeyStore.PrivateKeyEntry
    }

    fun getKeyPair(alias: String): KeyPair? {
        return getEntry(alias)?.let { KeyPair(it.certificate.publicKey, it.privateKey) }
    }

    fun getPrivateKey(alias: String): PrivateKey? {
        return getEntry(alias)?.privateKey
    }

    fun getPublicKey(alias: String): PublicKey? {
        return getEntry(alias)?.certificate?.publicKey
    }

    fun getAliases(): List<String> {
        return store.aliases().toList()
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