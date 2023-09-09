package io.opencola.cli

import io.opencola.security.hash.Hash
import io.opencola.security.hash.Sha256Hash
import io.opencola.security.keystore.JavaKeyStore
import io.opencola.util.toBase58
import java.nio.file.Path

fun keystore(storagePath: Path, keyStoreCommand: KeyStoreCommand, getPasswordHash: () -> Hash) {
    val keyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), getPasswordHash())
    if (keyStoreCommand.list == true) {
        keyStore.getAliases().forEach {
            keyStore.getKeyPair(it)?.let { keyPair ->
                println("alias=$it, public-key=${keyPair.public.encoded.toBase58()}, private-kKey=${keyPair.private.encoded.toBase58()}")
            }
        }
    } else if (keyStoreCommand.change != null) {
        // TODO: New password should be read with password prompt
        keyStore.changePassword(Sha256Hash.ofString(keyStoreCommand.change!!))
    }
}