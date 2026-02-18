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

import io.opencola.io.StdoutMonitor
import io.opencola.model.Id
import io.opencola.security.generateKeyPair
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import java.net.URI
import java.security.PublicKey

fun createPersona(name: String, isActive: Boolean = true): PersonaAddressBookEntry {
    val keyPair = generateKeyPair()
    val personaId = Id.ofPublicKey(keyPair.public)
    return PersonaAddressBookEntry(
        personaId,
        personaId,
        name,
        keyPair.public,
        URI("mock://$personaId"),
        null,
        isActive,
        keyPair
    )
}

fun createPeer(
    personaId: Id,
    name: String,
    isActive: Boolean = true,
    publicKey: PublicKey = generateKeyPair().public
): AddressBookEntry {
    val entityId = Id.ofPublicKey(publicKey)
    return AddressBookEntry(
        personaId,
        entityId,
        name,
        publicKey,
        URI("mock://$entityId"),
        null,
        isActive,
    )
}

fun AddressBook.addPersona(name: String, isActive: Boolean = true): PersonaAddressBookEntry {
    return createPersona(name, isActive).also { updateEntry(it) }
}

fun AddressBook.deletePersona(personaId: Id) {
    deleteEntry(personaId, personaId)
}

fun AddressBook.addPeer(personaId: Id,
                        name: String,
                        isActive: Boolean = true,
                        publicKey: PublicKey = generateKeyPair().public
): AddressBookEntry {
    return createPeer(personaId, name, isActive, publicKey).also { updateEntry(it) }
}

fun StdoutMonitor.waitForAddressBookAdd(personaId: Id, peerId: Id) {
    waitUntil {it.contains("Address book update: null -> AddressBookEntry(personaId=$personaId, entityId=$peerId") }
}


