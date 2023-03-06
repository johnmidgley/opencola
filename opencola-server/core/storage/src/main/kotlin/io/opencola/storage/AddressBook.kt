package io.opencola.storage

import mu.KotlinLogging
import io.opencola.model.Authority
import io.opencola.model.CoreAttribute
import io.opencola.model.Id
import io.opencola.model.Operation
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

    private val logger = KotlinLogging.logger("AddressBook")
    private val activeTag = "active"
    private val entityStore = ExposedEntityStore(SQLiteDB(storagePath.resolve("address-book.db")).db, Signator(keyStore), KeyStorePublicKeyProvider(keyStore))
    private val updateHandlers = CopyOnWriteArrayList<(AddressBookEntry?, AddressBookEntry?) -> Unit>()

    init {
        // Prior to personas, the default for the root authority was not activated by default, and an active check was
        // not applied to incoming requests. If we leave this situation as is, when someone upgrades to personas, the
        // single persona will be inactive, and will stop synchronizing with peers. We "fix" this by looking at the
        // history of the address book, and if there has only ever been one persona
        // (authority with authorityId == entityId), which has never been active (there is no fact for the persona
        // asserting an active tag), we activate it.
        val facts = entityStore.getFacts(emptySet(), emptySet())

        val personaIds = facts
            .filter { it.authorityId == it.entityId }
            .map { it.authorityId }
            .distinct()

        if (personaIds.count() == 1) {
            val personaId = personaIds.first()

            val activeFact = facts.firstOrNull {
                it.authorityId == personaId
                        && it.entityId == personaId
                        && it.attribute == CoreAttribute.Tags.spec
                        && it.operation == Operation.Add
                        && it.value.bytes.contentEquals(activeTag.toByteArray())
            }

            if(activeFact == null) {
                logger.warn { "Activating persona $personaId" }
                val persona = entityStore.getEntity(personaId, personaId) as Authority
                persona.tags += activeTag
                entityStore.updateEntities(persona)
            }
        }
    }

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
            imageUri = entry.imageUri,
            tags = if (entry.isActive) setOf(activeTag) else emptySet()
        )
    }

    private fun updateKeyStore(addressBookEntry: AddressBookEntry) {
        if (addressBookEntry is PersonaAddressBookEntry) {
            keyStore.addKey(addressBookEntry.entityId.toString(), addressBookEntry.keyPair)
        }
    }

    private fun updateAuthority(authority: Authority, entry: AddressBookEntry) : Authority {
        if (authority.publicKey != entry.publicKey)
            throw IllegalArgumentException("Public key cannot be updated")

        authority.uri = entry.address.normalize().trim()
        authority.name = entry.name.blankToNull() ?: throw IllegalArgumentException("Name cannot be blank")
        authority.imageUri = entry.imageUri
        authority.tags = if (entry.isActive) setOf(activeTag) else emptySet()

        return authority
    }

    // TODO: suppressUpdateHandler looks like it's misnamed - is it even ever used?
    fun updateEntry(entry: AddressBookEntry,
                    suppressUpdateHandler: ((AddressBookEntry?, AddressBookEntry?) -> Unit)? = null
    ) : AddressBookEntry {
        // Persona private keys are stored in the key store
        updateKeyStore(entry)

        // If we're updating a peer that is connected to more than one persona, we need to update all of them
        // Since personas only have a single entry, with entityId = personaId, grabbing all authorities with the entity
        // id grabs the correct list
        val existingEntities = entityStore.getEntities(emptySet(), setOf(entry.entityId)).filterIsInstance<Authority>()

        // Note: since peers with from personas (authorities) may be updated at the same time, we cannot do all
        // updates in a single transaction, as transactions are bound to a single authority.
        class Update(val previousEntry: AddressBookEntry?, val updatedAuthority: Authority)
        existingEntities
            .partition { it.authorityId == entry.personaId && it.entityId == entry.entityId }
            .let { (exactMatch, otherPeers) ->
                exactMatch
                    .map { Update(authorityToEntry(it), updateAuthority(it, entry)) }
                    .ifEmpty { listOf(Update(null, entryToAuthority(entry))) }
                    .plus(otherPeers.map { Update(authorityToEntry(it), updateAuthority(it, entry)) })
                    .forEach { update ->
                        entityStore.updateEntities(update.updatedAuthority)?.let {
                            val authority = update.updatedAuthority
                            val updatedAuthority =
                                entityStore.getEntity(authority.authorityId, authority.entityId) as Authority
                            val updatedEntry = authorityToEntry(updatedAuthority)
                            callUpdateHandlers(update.previousEntry, updatedEntry, suppressUpdateHandler)
                        }
                    }
            }

        return authorityToEntry(entityStore.getEntity(entry.personaId, entry.entityId) as Authority)
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
        val entries = getEntries()
        val personas = entries.filterIsInstance<PersonaAddressBookEntry>()
        val deletingPersona = personaId == entityId

        if(deletingPersona && personas.singleOrNull()?.personaId == personaId){
            throw IllegalArgumentException("Can't delete only persona from address book.")
        }

       entries
           .filter { it.personaId == personaId && (deletingPersona || it.entityId == entityId) }
           .sortedByDescending { it.entityId == personaId }
           .forEach{
               entityStore.deleteEntity(it.personaId, it.entityId)
               callUpdateHandlers(it, null)
           }

        if(deletingPersona)
            keyStore.deleteKeyPair(personaId.toString())
    }

    override fun getPublicKey(alias: Id): PublicKey? {
        return getEntries().firstOrNull { it.entityId == alias }?.publicKey
    }
}