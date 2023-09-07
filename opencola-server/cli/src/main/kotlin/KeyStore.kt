package io.opencola.cli

import io.opencola.security.JavaKeyStore
import io.opencola.util.toBase58
import java.nio.file.Path

fun keystore(storagePath: Path, keyStoreCommand: KeyStoreCommand, getPassword: () -> String) {
    val password = getPassword()
    val keyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), password)
    if (keyStoreCommand.list == true) {
        keyStore.getAliases().forEach {
            keyStore.getKeyPair(it)?.let { keyPair ->
                println("alias=$it, public-key=${keyPair.public.encoded.toBase58()}, private-kKey=${keyPair.private.encoded.toBase58()}")
            }
        }
    } else if (keyStoreCommand.change != null) {
        keyStore.changePassword(keyStoreCommand.change!!)
    }
}