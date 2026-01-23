/*
 * Copyright 2024-2026 OpenCola
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

package io.opencola.storage

import io.opencola.model.Attributes
import io.opencola.model.ResourceEntity
import io.opencola.security.keystore.JavaKeyStore
import io.opencola.security.keystore.KeyStore
import io.opencola.security.MockKeyStore
import io.opencola.security.Signator
import io.opencola.security.hash.Hash
import io.opencola.security.keystore.defaultPasswordHash
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.EntityStoreAddressBook
import io.opencola.storage.addressbook.EntityStoreAddressBook.Version
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.entitystore.ExposedEntityStoreV2
import io.opencola.storage.db.getSQLiteDB
import java.net.URI
import java.nio.file.Path

class ExposedEntityStoreContext(
    val storagePath: Path,
    val password: Hash = defaultPasswordHash,
    val keyStore: KeyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), password),
    val signator: Signator = Signator(keyStore),
    val addressBook: AddressBook = EntityStoreAddressBook(
        Version.V2,
        storagePath.resolve("address-book"),
        keyStore
    ),
    val entityStore: EntityStore = ExposedEntityStoreV2(
        "entity-store",
        storagePath.resolve("entity-store"),
        ::getSQLiteDB,
        Attributes.get(),
        signator,
        addressBook
    )
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