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

package io.opencola.storage.addressbook

import io.opencola.model.Id
import io.opencola.security.PublicKeyProvider

interface AddressBook : PublicKeyProvider<Id> {
    fun addUpdateHandler(handler: (AddressBookEntry?, AddressBookEntry?) -> Unit)
    fun removeUpdateHandler(handler: (AddressBookEntry?, AddressBookEntry?) -> Unit)
    fun updateEntry(
        entry: AddressBookEntry,
        suppressUpdateHandler: ((AddressBookEntry?, AddressBookEntry?) -> Unit)? = null
    ): AddressBookEntry
    fun getEntry(personaId: Id, id: Id) : AddressBookEntry?
    fun getEntries() : List<AddressBookEntry>
    fun deleteEntry(personaId: Id, entityId: Id)

    // Convenience methods
    fun getPersonas() : List<PersonaAddressBookEntry> = getEntries().filterIsInstance<PersonaAddressBookEntry>()
    fun getPeers() : List<AddressBookEntry> = getEntries().filter { it !is PersonaAddressBookEntry }
}