package io.opencola.cli

import io.opencola.security.JavaKeyStore
import java.nio.file.Path

fun keystore(storagePath: Path, keyStoreCommand: KeyStoreCommand, getPassword: () -> String) {
    val password = getPassword()
    val keyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), password)
    if (keyStoreCommand.list == true) {
        println("Aliases:")
        keyStore.getAliases().forEach { println(it) }
    } else if (keyStoreCommand.change != null) {
        keyStore.changePassword(keyStoreCommand.change!!)
    }
}