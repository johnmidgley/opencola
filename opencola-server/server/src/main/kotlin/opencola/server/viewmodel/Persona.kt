package opencola.server.viewmodel

import io.opencola.model.Id
import io.opencola.security.decodePublicKey
import io.opencola.security.encode
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.PersonaAddressBookEntry
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
                personaAddressBookEntry.imageUri.toString(),
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