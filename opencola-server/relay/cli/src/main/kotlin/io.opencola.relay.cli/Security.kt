package io.opencola.relay.cli

import io.opencola.security.generateKeyPair
import io.opencola.security.hash.Hash
import io.opencola.security.hash.Sha256Hash
import io.opencola.security.keystore.JavaKeyStore
import java.nio.file.Path
import java.security.KeyPair
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

fun readPassword(): String {
    val console = System.console()

    return if (console != null) {
        val password = console.readPassword()
        String(password)
    } else {
        readln()
    }
}

fun getPasswordHash(config: Config): Hash {
    val password = config.ocr.credentials.password

    return Sha256Hash.ofString(
        if (password != null)
            password
        else {
            println("You can set the OCR_PASSWORD environment variable to avoid having to enter your password.")
            print("Password: ")
            readPassword()
        }
    )
}

private const val keyStoreName = "keystore.pks"
private const val rootKeyAlias = "ocr"

fun createKeyStore(path: Path, passwordHash: Hash): JavaKeyStore {
    path.parent.createDirectories()
    return JavaKeyStore(path, passwordHash)
}

fun getKeyPair(storagePath: Path, passwordHash: Hash): KeyPair? {
    val path = storagePath.resolve(keyStoreName)

    if (!path.exists()) {
        val keyStore = createKeyStore(path, passwordHash)
        keyStore.addKeyPair(rootKeyAlias, generateKeyPair())
    }

    return JavaKeyStore(path, passwordHash).getKeyPair(rootKeyAlias)
}