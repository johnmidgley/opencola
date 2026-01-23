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

package opencola.server.handlers

import io.opencola.model.Id
import io.opencola.security.generateKeyPair
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import kotlinx.serialization.Serializable
import opencola.server.viewmodel.Persona
import java.net.URI

@Serializable
data class PersonasResult(val items: List<Persona>)

fun createPersona(addressBook: AddressBook, persona: Persona) : Persona {
    require(persona.id.isBlank()) { "Persona id must be blank" }
    require(persona.name.isNotBlank()) { "Persona name must not be blank" }
    require(persona.publicKey.isBlank()) { "Persona public key must be blank" }
    require(persona.address.isNotBlank()) { "Persona address must not be blank" }

    val keyPair = generateKeyPair()
    val personaId = Id.ofPublicKey(keyPair.public)
    val personaAddressBookEntry = PersonaAddressBookEntry(
        personaId,
        personaId,
        persona.name,
        keyPair.public,
        URI(persona.address),
        persona.imageUri?.let { URI(it) },
        persona.isActive,
        keyPair
    )

    addressBook.updateEntry(personaAddressBookEntry)
    return Persona(personaAddressBookEntry)
}

fun getPersona(addressBook: AddressBook, personaId: Id) : Persona {
    val personaAddressBookEntry = addressBook.getEntry(personaId, personaId) as PersonaAddressBookEntry
    return Persona(personaAddressBookEntry)
}

fun updatePersona(addressBook: AddressBook, persona: Persona) : Persona {
    val addressBookEntry = persona.toAddressBookEntry().also { addressBook.updateEntry(it) }
    val personaAddressBookEntry = addressBook.getEntry(addressBookEntry.personaId, addressBookEntry.personaId) as PersonaAddressBookEntry
    return Persona(personaAddressBookEntry)
}

fun deletePersona(addressBook: AddressBook, personaId: Id) {
    addressBook.deleteEntry(personaId, personaId)
}

fun getPersonas(addressBook: AddressBook) : PersonasResult {
    return PersonasResult(
        addressBook
            .getEntries()
            .filterIsInstance<PersonaAddressBookEntry>()
            .sortedBy { it.name.lowercase() }
            .map { Persona(it) }
    )
}