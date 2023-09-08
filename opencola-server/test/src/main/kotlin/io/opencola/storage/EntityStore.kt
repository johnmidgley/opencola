package io.opencola.storage

import io.opencola.model.ResourceEntity
import io.opencola.security.keystore.JavaKeyStore
import io.opencola.security.keystore.KeyStore
import io.opencola.security.MockKeyStore
import io.opencola.security.Signator
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookConfig
import io.opencola.storage.addressbook.EntityStoreAddressBook
import io.opencola.storage.addressbook.EntityStoreAddressBook.Version
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.entitystore.ExposedEntityStore
import io.opencola.storage.entitystore.getSQLiteDB
import java.net.URI
import java.nio.file.Path

class ExposedEntityStoreContext(
    val storagePath: Path,
    val password: String = "password",
    val keyStore: KeyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), password),
    val signator: Signator = Signator(keyStore),
    val addressBook: AddressBook = EntityStoreAddressBook(Version.V2, AddressBookConfig(), storagePath, keyStore),
    val entityStore: EntityStore = ExposedEntityStore("entity-store", storagePath, ::getSQLiteDB, signator, addressBook)
)

class EntityStoreContext(
    val keyStore: KeyStore = MockKeyStore(),
    val signator: Signator = Signator(keyStore),
    val addressBook: AddressBook = MockAddressBook(keyStore),
    val entityStore: EntityStore = MockEntityStore(signator, addressBook)
)

fun getTestEntity(persona: PersonaAddressBookEntry, num: Int): ResourceEntity {
    return ResourceEntity(persona.entityId, URI("https://test.com/$num"), "TestEntity $num")
}