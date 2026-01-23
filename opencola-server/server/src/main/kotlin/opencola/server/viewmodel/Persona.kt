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

package opencola.server.viewmodel

import io.opencola.model.Id
import io.opencola.security.decodePublicKey
import io.opencola.security.encode
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class Persona(
    val id: String,
    val name: String,
    val publicKey: String,
    val address: String,
    val imageUri: String?,
    val isActive: Boolean,
) {
    constructor(personaAddressBookEntry: PersonaAddressBookEntry) :
            this(
                personaAddressBookEntry.entityId.toString(),
                personaAddressBookEntry.name,
                personaAddressBookEntry.publicKey.encode(),
                personaAddressBookEntry.address.toString(),
                personaAddressBookEntry.imageUri?.toString(),
                personaAddressBookEntry.isActive
            )

    fun toAddressBookEntry() : AddressBookEntry {
        val id = Id.decode(id)
        return AddressBookEntry(
            id,
            id,
            name,
            decodePublicKey(publicKey),
            URI(address),
            imageUri?.let { URI(it) },
            isActive
        )
    }
}