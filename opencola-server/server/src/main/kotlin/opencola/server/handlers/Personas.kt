package opencola.server.handlers

import io.opencola.model.Persona
import io.opencola.storage.AddressBook
import opencola.server.model.Persona as UIPersona

fun getPersonas(addressBook: AddressBook) : List<UIPersona> {
    return addressBook.getAuthorities().filterIsInstance<Persona>().map { UIPersona(it) }
}