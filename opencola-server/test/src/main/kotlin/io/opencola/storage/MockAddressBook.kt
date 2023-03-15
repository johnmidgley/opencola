package io.opencola.storage

import io.opencola.model.Id
import io.opencola.security.KeyStore
import io.opencola.security.MockKeyStore
import io.opencola.security.generateKeyPair
import java.security.PublicKey

class MockAddressBook(val keyStore: KeyStore = MockKeyStore()) : AddressBook {
    private var _entries = List<AddressBookEntry>(0) { throw Exception("Not implemented") }
    override fun addUpdateHandler(handler: (AddressBookEntry?, AddressBookEntry?) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun removeUpdateHandler(handler: (AddressBookEntry?, AddressBookEntry?) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun updateEntry(
        entry: AddressBookEntry,
        suppressUpdateHandler: ((AddressBookEntry?, AddressBookEntry?) -> Unit)?
    ): AddressBookEntry {
        _entries = _entries
            .filter { it.personaId == entry.personaId && it.entityId != entry.entityId }
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

    override fun getPublicKey(alias: Id): PublicKey? {
        return getEntry(alias, alias)?.publicKey
    }

    fun addPersona(name: String, isActive: Boolean = true): PersonaAddressBookEntry {
        return createPersona(name, isActive).also { updateEntry(it) }
    }

    fun addPeer(personaId: Id,
                name: String,
                isActive: Boolean = true,
                publicKey: PublicKey = generateKeyPair().public
    ): AddressBookEntry {
        return createPeer(personaId, name, isActive, publicKey).also { updateEntry(it) }
    }
}