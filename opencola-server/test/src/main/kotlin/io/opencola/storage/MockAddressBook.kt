package io.opencola.storage

import io.opencola.model.Id
import io.opencola.security.keystore.KeyStore
import io.opencola.security.MockKeyStore
import io.opencola.storage.addressbook.AbstractAddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry

class MockAddressBook(private val keyStore: KeyStore = MockKeyStore()) : AbstractAddressBook() {
    private var _entries = List<AddressBookEntry>(0) { throw Exception("Not implemented") }
    override fun updateEntry(
        entry: AddressBookEntry,
        suppressUpdateHandler: ((AddressBookEntry?, AddressBookEntry?) -> Unit)?
    ): AddressBookEntry {
        _entries = _entries
            .filter { it.personaId != entry.personaId || it.entityId != entry.entityId }
            .plus(entry)

        if(entry is PersonaAddressBookEntry) {
            keyStore.addKeyPair(entry.personaId.toString(), entry.keyPair)
        }

        return entry
    }

    override fun getEntry(personaId: Id, id: Id): AddressBookEntry? {
        return _entries.firstOrNull { it.entityId == id && it.personaId == personaId }
    }

    override fun getEntries(): List<AddressBookEntry> {
        return _entries
    }

    override fun deleteEntry(personaId: Id, entityId: Id) {
        val entry = getEntry(personaId, entityId)

        if(entry is PersonaAddressBookEntry) {
            keyStore.deleteKeyPair(entry.personaId.toString())
        }

        _entries = _entries
            .filter {  it.personaId == personaId && it.entityId != entityId }
    }
}