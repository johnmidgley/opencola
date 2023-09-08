package io.opencola.security.keystore

import io.opencola.security.SecurityProviderDependent
import io.opencola.security.hash.Sha256Hash
import io.opencola.security.certificate.createCertificate
import mu.KotlinLogging
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyStore as javaKeyStore
import java.security.PublicKey
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

// TODO: Add logging
// TODO: Think about an appropriate HSM
// TODO: Investigate secure strings
// TODO: Make abstract keystore that is base for cert store and authority store
// TODO: Make AuthorityKeyStore, that has encrypt and sign methods
class JavaKeyStore(val path: Path, password: String) : SecurityProviderDependent(), KeyStore {
    val logger = KotlinLogging.logger(this.javaClass.simpleName)

    // TODO: Move parameters to config
    private var store: javaKeyStore = javaKeyStore.getInstance("PKCS12","BC")
    private lateinit var passwordHash: CharArray//  = sha256(password).toHexString().toCharArray()
    private lateinit var protectionParameter: javaKeyStore.PasswordProtection// = KeyStore.PasswordProtection(passwordHash)

    private fun setPassword(password: String) {
        passwordHash = Sha256Hash.ofString(password).toHexString().toCharArray()
        protectionParameter = javaKeyStore.PasswordProtection(passwordHash)
    }

    init{
        setPassword(password)
        (if(path.exists()) path.inputStream() else null).let {
            store.load(it, passwordHash)
        }
    }

    private fun saveStore() {
        path.outputStream().use {
            store.store(it, passwordHash)
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

    override fun changePassword(newPassword: String) {
        val newPasswordHash = Sha256Hash.ofString(newPassword).toHexString().toCharArray()
        changePassword(path, String(passwordHash), String(newPasswordHash))
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

fun changePassword(keyStorePath: Path, oldPassword: String, newPassword: String) {
    val store = javaKeyStore.getInstance("PKCS12", "BC")

    keyStorePath.inputStream().use { inputStream ->
        store.load(inputStream, oldPassword.toCharArray())

        keyStorePath.outputStream().use {
            store.store(it, newPassword.toCharArray())
        }
    }
}