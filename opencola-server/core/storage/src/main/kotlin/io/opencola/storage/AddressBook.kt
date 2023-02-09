package io.opencola.storage

import mu.KotlinLogging
import io.opencola.model.Authority
import io.opencola.model.Id
import io.opencola.security.KeyStore
import io.opencola.security.PublicKeyProvider
import io.opencola.security.Signator
import io.opencola.util.blankToNull
import io.opencola.util.trim
import java.lang.StringBuilder
import java.nio.file.Path
import java.security.PublicKey
import java.util.concurrent.CopyOnWriteArrayList

class AddressBook(private val storagePath: Path, private val keyStore: KeyStore) : PublicKeyProvider<Id> {
    class KeyStorePublicKeyProvider(private val keyStore: KeyStore) : PublicKeyProvider<Id> {
        override fun getPublicKey(alias: Id): PublicKey? {
            return keyStore.getPublicKey(alias.toString())
        }
    }

    val logger = KotlinLogging.logger("AddressBook")

    private val activeTag = "active"
    private val entityStore = ExposedEntityStore(SQLiteDB(storagePath.resolve("address-book.db")).db, Signator(keyStore), KeyStorePublicKeyProvider(keyStore))
    private val updateHandlers = CopyOnWriteArrayList<(AddressBookEntry?, AddressBookEntry?) -> Unit>()

    override fun toString(): String {
        val stringBuilder = StringBuilder("AddressBook($storagePath){\n")
        val (personas, peers) = getEntries().partition { it is PersonaAddressBookEntry }

        personas.forEach { stringBuilder.appendLine("\tPersona: $it") }
        peers.forEach { stringBuilder.appendLine("\tPeer: $it") }
        stringBuilder.appendLine("}")

        return stringBuilder.toString()
    }

    // TODO: Move to Authority - Actually - don't. This is address book specific
    fun isAuthorityActive(authority: Authority) : Boolean {
        return authority.tags.contains(activeTag)
    }

    fun addUpdateHandler(handler: (AddressBookEntry?, AddressBookEntry?) -> Unit){
        updateHandlers.add(handler)
    }

    fun removeUpdateHandler(handler: (AddressBookEntry?, AddressBookEntry?) -> Unit){
        updateHandlers.remove(handler)
    }

    private fun callUpdateHandlers(previousAuthority: AddressBookEntry?,
                                   currentAuthority: AddressBookEntry?,
                                   suppressUpdateHandler: ((AddressBookEntry?, AddressBookEntry?) -> Unit)? = null) {
        updateHandlers.forEach {
            try {
                if(it != suppressUpdateHandler)
                    it(previousAuthority, currentAuthority)
            } catch (e: Exception) {
                logger.error { "Error calling update handler: $e" }
            }
        }
    }

    private fun authorityToEntry(authority: Authority): AddressBookEntry {
        return keyStore.getKeyPair(authority.entityId.toString())?.let {
            PersonaAddressBookEntry(authority, it)
        } ?: AddressBookEntry(authority)
    }

    private fun entryToAuthority(entry: AddressBookEntry) : Authority {
        return Authority(
            entry.personaId,
            entry.publicKey,
            entry.address.normalize().trim(),
            entry.name,
            imageUri = entry.imageUri
        )
    }

    private fun updateKeyStore(addressBookEntry: AddressBookEntry) {
        if (addressBookEntry is PersonaAddressBookEntry) {
            keyStore.addKey(addressBookEntry.entityId.toString(), addressBookEntry.keyPair)
        }
    }

    private fun updateAuthority(authority: Authority, entry: AddressBookEntry) {
        if (authority.publicKey != entry.publicKey)
            throw IllegalArgumentException("Public key cannot be updated")

        authority.uri = entry.address.normalize().trim()
        authority.name = entry.name.blankToNull() ?: throw IllegalArgumentException("Name cannot be blank")
        authority.imageUri = entry.imageUri
        authority.tags = if (entry.isActive) setOf(activeTag) else emptySet()
    }

    // TODO: suppressUpdateHandler looks like it's misnamed - is it even ever used?
    fun updateEntry(entry: AddressBookEntry,
                    suppressUpdateHandler: ((AddressBookEntry?, AddressBookEntry?) -> Unit)? = null) : AddressBookEntry {
        val existingAuthority = entityStore.getEntity(entry.personaId, entry.entityId) as Authority?
        val previousEntry = existingAuthority?.let { authorityToEntry(it) }
        val authorityToUpdate = existingAuthority ?: entryToAuthority(entry)

        // TODO: Force peers across personas to be the same
        updateKeyStore(entry)
        updateAuthority(authorityToUpdate, entry)

        return if (entityStore.updateEntities(authorityToUpdate) == null) {
            // Nothing changed
            entry
        } else {
            // Something changed - inform subscribers
            authorityToEntry(entityStore.getEntity(entry.personaId, entry.entityId) as Authority).also {
                callUpdateHandlers(previousEntry, it, suppressUpdateHandler)
            }
        }
    }

    fun getEntry(personaId: Id, id: Id) : AddressBookEntry? {
       return (entityStore.getEntity(personaId, id) as Authority?)?.let { authorityToEntry(it) }
    }

    fun getEntries() : List<AddressBookEntry> {
        return entityStore.getEntities(emptySet(), emptySet())
            .filterIsInstance<Authority>()
            .map { authorityToEntry(it) }
    }

    fun deleteEntry(personaId: Id, entityId: Id) {
        val personas = getEntries().filterIsInstance<PersonaAddressBookEntry>()

        val persona = personas.firstOrNull { it.personaId == personaId && it.entityId == personaId }
            ?: throw IllegalArgumentException("Invalid persona id: $personaId")

        if(personas.count() == 1 && personaId == entityId)
            throw IllegalArgumentException("Can't delete only persona from address book.")

        val previousAuthority = entityStore.getEntity(persona.personaId, entityId) as? Authority
            ?: throw IllegalStateException("Attempt to delete non existent authority: personaId=$personaId, id=$entityId")

        entityStore.deleteEntity(personaId, entityId)
        callUpdateHandlers(AddressBookEntry(previousAuthority), null)
    }

    override fun getPublicKey(alias: Id): PublicKey? {
        return getEntries().firstOrNull { it.entityId == alias }?.publicKey
    }
}