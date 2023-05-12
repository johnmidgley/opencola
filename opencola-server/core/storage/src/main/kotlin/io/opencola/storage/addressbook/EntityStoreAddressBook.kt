package io.opencola.storage.addressbook

import io.opencola.model.*
import io.opencola.model.value.StringValue
import io.opencola.security.KeyStore
import io.opencola.security.Signator
import io.opencola.storage.entitystore.*
import io.opencola.util.blankToNull
import io.opencola.util.trim
import java.nio.file.Path

class EntityStoreAddressBook(
    val version: Version,
    val config: AddressBookConfig,
    private val storagePath: Path,
    private val keyStore: KeyStore,
) : AbstractAddressBook() {
    private val name = "address-book"
    enum class Version {
        V1,
        V2
    }

    private val entityStore =
        when (version) {
            Version.V1 -> ExposedEntityStore(
                name,
                storagePath,
                ::getSQLiteDB,
                Signator(keyStore),
                KeyStorePublicKeyProvider(keyStore)
            )

            Version.V2 -> ExposedEntityStoreV2(
                name,
                EntityStoreConfig(config.transactionStorageUri),
                storagePath,
                ::getSQLiteDB,
                Attributes.get(),
                Signator(keyStore),
                KeyStorePublicKeyProvider(keyStore)
            )
        }


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
                        && (it.value as StringValue).get() == activeTag
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
        return "EntityStoreAddressBook(version=$version storagePath=$storagePath, config=$config)"
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
            tags = if (entry.isActive) listOf(activeTag) else emptyList()
        )
    }

    private fun updateKeyStore(addressBookEntry: AddressBookEntry) {
        if (addressBookEntry is PersonaAddressBookEntry) {
            keyStore.addKeyPair(addressBookEntry.entityId.toString(), addressBookEntry.keyPair)
        }
    }

    private fun updateAuthority(authority: Authority, entry: AddressBookEntry) : Authority {
        if (authority.publicKey != entry.publicKey)
            throw IllegalArgumentException("Public key cannot be updated")

        authority.uri = entry.address.normalize().trim()
        authority.name = entry.name.blankToNull() ?: throw IllegalArgumentException("Name cannot be blank")
        authority.imageUri = entry.imageUri
        authority.tags = if (entry.isActive) listOf(activeTag) else emptyList()

        return authority
    }

    // TODO: suppressUpdateHandler looks like it's misnamed - is it even ever used?
    // TODO: The synchronization code should be abstracted into AbstractAddressBook
    override fun updateEntry(entry: AddressBookEntry,
                             suppressUpdateHandler: ((AddressBookEntry?, AddressBookEntry?) -> Unit)?
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

    override fun getEntry(personaId: Id, id: Id) : AddressBookEntry? {
        return (entityStore.getEntity(personaId, id) as Authority?)?.let { authorityToEntry(it) }
    }

    override fun getEntries() : List<AddressBookEntry> {
        return entityStore.getEntities(emptySet(), emptySet())
            .filterIsInstance<Authority>()
            .map { authorityToEntry(it) }
    }

    override fun deleteEntry(personaId: Id, entityId: Id) {
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
                entityStore.deleteEntities(it.personaId, it.entityId)
                callUpdateHandlers(it, null)
            }

        if(deletingPersona)
            keyStore.deleteKeyPair(personaId.toString())
    }
}