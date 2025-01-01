/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.cli

import io.opencola.model.Attributes
import io.opencola.security.Signator
import io.opencola.security.keystore.JavaKeyStore
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.EntityStoreAddressBook
import io.opencola.storage.addressbook.EntityStoreAddressBook.Version
import io.opencola.storage.db.getSQLiteDB
import io.opencola.storage.entitystore.ExposedEntityStoreV2
import io.opencola.storage.filestore.ContentAddressedFileStore
import io.opencola.storage.filestore.FileSystemContentAddressedFileStore
import io.opencola.storage.filestore.FileSystemIdAddressedFileStore
import io.opencola.storage.filestore.IdAddressedFileStore
import java.nio.file.Path
import kotlin.io.path.exists

class Context(
    val config: Config,
    val storagePath: Path,
    val keyStore: JavaKeyStore,
    val addressBook: AddressBook,
    val entityStore: ExposedEntityStoreV2,
    val txFileStore: IdAddressedFileStore,
    val contentFileStore: ContentAddressedFileStore
)

fun getContext(storagePath: Path): Context {
    val config = loadConfig()
    val passwordHash = getPasswordHash(config)

    val keyStorePath = storagePath.resolve("keystore.pks")
    if(!keyStorePath.exists()) {
        error("Could not find keystore. Are you in an OC storage directory?")
    }
    val keyStore = JavaKeyStore(keyStorePath, passwordHash)

    val addressBookPath = storagePath.resolve( "address-book")
    if(!addressBookPath.exists()) {
        error("Could not find address-book. Are you in an OC storage directory?")
    }
    val addressBook = EntityStoreAddressBook(Version.V2, storagePath.resolve( "address-book"), keyStore)


    val entityStorePath = storagePath.resolve( "entity-store")
    if(!entityStorePath.exists()) {
        error("Could not find entity-store. Are you in an OC storage directory?")
    }
    val entityStore = ExposedEntityStoreV2(
        "entity-store",
        entityStorePath,
        ::getSQLiteDB,
        Attributes.get(),
        Signator(keyStore),
        addressBook
    )

    val txFilePath = entityStorePath.resolve("transactions")
    if(!txFilePath.exists()) {
        error("Could not find transactions directory. Are you in an OC storage directory?")
    }
    val txFileStore = FileSystemIdAddressedFileStore(txFilePath)

    val contentFileStorePath = storagePath.resolve( "filestore")
    if(!contentFileStorePath.exists()) {
        error("Could not find  filestore. Are you in an OC storage directory?")
    }
    val contentFileStore = FileSystemContentAddressedFileStore(contentFileStorePath)

    return Context(config, storagePath, keyStore, addressBook, entityStore, txFileStore, contentFileStore)
}