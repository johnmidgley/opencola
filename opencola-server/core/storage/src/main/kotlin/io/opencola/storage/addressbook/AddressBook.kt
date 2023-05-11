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
}