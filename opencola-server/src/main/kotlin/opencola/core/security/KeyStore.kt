package opencola.core.security

import mu.KotlinLogging
import opencola.core.extensions.toHexString
import opencola.core.model.Id
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
    private var passwordHash = sha256(password).toHexString().toCharArray()
    private var protectionParameter = KeyStore.PasswordProtection(passwordHash)

    init{
        store.load(if(path.exists()) path.inputStream() else null, passwordHash)
    }

    fun addKey(id: Id, keyPair: KeyPair){
        store.setKeyEntry(id.toString(), keyPair.private, null, arrayOf(createCertificate(id, keyPair)))
        store.store(path.outputStream(), passwordHash)
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
}