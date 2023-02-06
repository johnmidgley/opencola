package opencola.server.handlers

import io.opencola.model.Persona
import io.opencola.security.generateKeyPair
import io.opencola.storage.AddressBook
import java.net.URI
import opencola.server.model.Persona as UIPersona

fun getPersonas(addressBook: AddressBook) : List<UIPersona> {
    return addressBook.getAuthorities().filterIsInstance<Persona>().map { UIPersona(it) }
}

fun createPersona(addressBook: AddressBook, persona: UIPersona) : UIPersona {
    val newPersona = Persona(
        generateKeyPair(),
        URI(persona.address),
        persona.name,
    )

    newPersona.imageUri = persona.imageUri?.let { URI(it) }
    newPersona.setActive(persona.isActive)

    addressBook.updateAuthority(newPersona)
    return UIPersona(newPersona)
}