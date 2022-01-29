package opencola.core.security

import opencola.core.extensions.nullOrElse
import opencola.core.extensions.toHexString
import opencola.core.model.Id
import java.nio.file.Path
import java.security.*
import java.security.KeyStore
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

// TODO: Add logging
// TODO: Think about an appropriate HSM
// TODO: Investigate secure strings
class KeyStore(val path: Path, password: String) : SecurityProviderDependent() {
    // TODO: Move parameters to config
    var store: KeyStore = KeyStore.getInstance("PKCS12","BC")
    private var passwordHash = sha256(password).toHexString().toCharArray()

    init{
        store.load(if(path.exists()) path.inputStream() else null, passwordHash)
    }

    fun addKey(id: Id, keyPair: KeyPair){
        store.setKeyEntry(id.toString(), keyPair.private, null, arrayOf(createCertificate(id, keyPair)))
        store.store(path.outputStream(), passwordHash)
    }

    fun getPrivateKey(id: Id): PrivateKey? {
        return store.getKey(id.toString(), passwordHash)?.encoded.nullOrElse { privateKeyFromBytes(it) }
    }

    fun getPublicKey(id: Id): PublicKey? {
        return store.getCertificate(id.toString()).publicKey
    }
}