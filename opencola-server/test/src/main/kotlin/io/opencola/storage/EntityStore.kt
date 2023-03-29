package io.opencola.storage

import io.opencola.application.Application
import io.opencola.security.JavaKeyStore
import io.opencola.security.KeyStore
import io.opencola.security.Signator
import java.nio.file.Path

class ExposedEntityStoreContext(
    val storagePath: Path,
    val password: String = "password",
    val keyStore: KeyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), password),
    val signator: Signator = Signator(keyStore),
    val addressBook: AddressBook = EntityStoreAddressBook(storagePath, keyStore),
    val entityStore: EntityStore = ExposedEntityStore(Application.getEntityStoreDB(storagePath), signator, addressBook)
)