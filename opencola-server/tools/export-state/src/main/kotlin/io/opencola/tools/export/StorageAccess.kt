package io.opencola.tools.export

import io.opencola.model.Attributes
import io.opencola.model.Id
import io.opencola.security.Signator
import io.opencola.security.hash.Hash
import io.opencola.security.keystore.JavaKeyStore
import io.opencola.security.keystore.KeyStore
import io.opencola.security.keystore.defaultPasswordHash
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.EntityStoreAddressBook
import io.opencola.storage.addressbook.EntityStoreAddressBook.Version
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.db.getSQLiteDB
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.entitystore.ExposedEntityStoreV2
import io.opencola.storage.filestore.ContentAddressedFileStore
import io.opencola.storage.filestore.FileSystemContentAddressedFileStore
import mu.KotlinLogging
import java.nio.file.Path

private val logger = KotlinLogging.logger("StorageAccess")

/**
 * Provides read-only access to the original OpenCola storage directory.
 */
class StorageAccess(
    val storagePath: Path,
    password: Hash = defaultPasswordHash,
) {
    val keyStore: KeyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), password)
    val signator: Signator = Signator(keyStore)
    val addressBook: AddressBook = EntityStoreAddressBook(
        Version.V2,
        storagePath.resolve("address-book"),
        keyStore
    )
    val entityStore: EntityStore = ExposedEntityStoreV2(
        "entity-store",
        storagePath.resolve("entity-store"),
        ::getSQLiteDB,
        Attributes.get(),
        signator,
        addressBook
    )
    val contentFileStore: ContentAddressedFileStore =
        FileSystemContentAddressedFileStore(storagePath.resolve("filestore"))

    fun getPersonas(): List<PersonaAddressBookEntry> =
        addressBook.getEntries().filterIsInstance<PersonaAddressBookEntry>()

    fun getPeers(): List<AddressBookEntry> =
        addressBook.getEntries().filter { it !is PersonaAddressBookEntry }

    fun getPublicKey(authorityId: Id) = addressBook.getPublicKey(authorityId)
}
