package opencola.core.security

import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore.*

// TODO: Move to config
val keyStoreName = "keyStore"
val privateKeyAlias = "opencolaPrivateKey"
val keySize = 2048
val algorithm = "EC"

// TODO: Add logging
// TODO: Think about an appropriate HSM

fun getKeyStore(path: String, password: String){
    val keyStore = getInstance(getDefaultType())
    val passwordCharArray = password.toCharArray()

    FileInputStream(keyStoreName).use { fis -> keyStore.load(fis, passwordCharArray) }

    val protParam: ProtectionParameter = PasswordProtection(passwordCharArray)
    val privateKey = (keyStore.getEntry(privateKeyAlias, protParam) as PrivateKeyEntry).privateKey

    if(privateKey == null){
        val keyPair = generateKeyPair()
        val privateKeyEntry = PrivateKeyEntry(keyPair.private, null)

        // keyStore.setEntry(privateKeyAlias, privateKey)
        FileOutputStream(keyStoreName).use { fos -> keyStore.store(fos, passwordCharArray) }

    }
}