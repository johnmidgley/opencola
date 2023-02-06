package opencola.server.handlers

import io.opencola.model.Persona as ModelPersona
import io.opencola.security.generateKeyPair
import io.opencola.storage.AddressBook
import opencola.server.viewmodel.Persona
import java.net.URI

fun getPersonas(addressBook: AddressBook) : List<Persona> {
    return addressBook.getAuthorities().filterIsInstance<ModelPersona>().map { Persona(it) }
}

fun createPersona(addressBook: AddressBook, persona: Persona) : Persona {
    val newPersona = ModelPersona(
        generateKeyPair(),
        URI(persona.address),
        persona.name,
    )

    newPersona.imageUri = persona.imageUri?.let { URI(it) }
    newPersona.setActive(persona.isActive)

    addressBook.updateAuthority(newPersona)
    return Persona(newPersona)
}