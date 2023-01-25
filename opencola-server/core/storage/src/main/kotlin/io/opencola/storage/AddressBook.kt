package io.opencola.storage

import mu.KotlinLogging
import io.opencola.model.Authority
import io.opencola.model.Id
import io.opencola.model.Persona
import io.opencola.security.KeyStore
import io.opencola.security.Signator
import io.opencola.util.trim
import java.net.URI
import java.nio.file.Path
import java.security.PublicKey
import java.util.concurrent.CopyOnWriteArrayList

// TODO: Move ServerConfig to NetworkConfig?
class AddressBook(storagePath: Path, private val keyStore: KeyStore) {
    val logger = KotlinLogging.logger("AddressBook")

    private val activeTag = "active"
    private val entityStore = ExposedEntityStore(SQLiteDB(storagePath.resolve("address-book.db")).db, Signator(keyStore), this)
    private val updateHandlers = CopyOnWriteArrayList<(Authority?, Authority?) -> Unit>()

    // TODO: Needed?
    fun getPublicKey(personaId: Id, authorityId: Id): PublicKey? {
            return getAuthority(personaId, authorityId)?.publicKey
    }

    init {
        TODO("This is application logic - move to Application")
//        val addressBookAuthority = getAuthority(authority.authorityId) ?: updateAuthority(authority)
//
//        if(addressBookAuthority.uri.toString().isBlank()) {
//            // Fallback to local server address.
//            // TODO: Is there a better place for this?
//            addressBookAuthority.uri = URI("ocr://relay.opencola.net")
//            updateAuthority(addressBookAuthority)
//        }
    }

    // TODO: Move to Authority
    fun isAuthorityActive(authority: Authority) : Boolean {
        return authority.tags.contains(activeTag)
    }

    fun addUpdateHandler(handler: (Authority?, Authority?) -> Unit){
        updateHandlers.add(handler)
    }

    fun removeUpdateHandler(handler: (Authority?, Authority?) -> Unit){
        updateHandlers.remove(handler)
    }

    private fun callUpdateHandlers(previousAuthority: Authority?,
                                   currentAuthority: Authority?,
                                   suppressUpdateHandler: ((Authority?, Authority?) -> Unit)? = null) {
        updateHandlers.forEach {
            try {
                if(it != suppressUpdateHandler)
                    it(previousAuthority, currentAuthority)
            } catch (e: Exception) {
                logger.error { "Error calling update handler: $e" }
            }
        }
    }

    fun updateAuthority(authority: Authority,
                        suppressUpdateHandler: ((Authority?, Authority?) -> Unit)? = null) : Authority {
        val previousAuthority = entityStore.getEntity(authority.authorityId, authority.entityId) as Authority?

        // Normalize the URI to avoid multiple connections to the same server
        authority.uri = authority.uri?.normalize()?.trim()

        if(entityStore.updateEntities(authority) != null) {
            val currentAuthority = entityStore.getEntity(authority.authorityId, authority.entityId) as Authority
            callUpdateHandlers(previousAuthority, currentAuthority, suppressUpdateHandler)
            return currentAuthority
        }

        // Nothing changed
        return authority
    }

    private fun authorityAsPersona(authority: Authority) : Authority {
        return keyStore.getKeyPair(authority.entityId.toString())?.let { Persona(authority, it) } ?: authority
    }

    // TODO: Consider a get peer method, that returns authorities across personas
    fun getAuthority(personaId: Id, id: Id) : Authority? {
       return (entityStore.getEntity(personaId, id) as Authority?)?.let { authorityAsPersona(it) }
    }

    fun getPeer(peerId: Id) : Set<Authority> {
        return entityStore.getEntities(emptySet(), setOf(peerId))
            .filterIsInstance<Authority>()
            .filter { it !is Persona && it.entityId == peerId }
            .toSet()
    }

    fun getPersona(personaId: Id) : Persona? {
        return entityStore.getEntity(personaId, personaId)?.let { authorityAsPersona(it as Authority) as? Persona }
    }

    // TODO: Make isActive be a property of Authority and remove filter here (caller can do it)
    fun getAuthorities(filterActive: Boolean = false) : Set<Authority> {
        return entityStore.getEntities(emptySet(), emptySet())
            .filterIsInstance<Authority>()
            .filter { !filterActive || it.tags.contains(activeTag)}
            .map { authorityAsPersona(it) }
            .toSet()
    }

    fun deleteAuthority(personaId: Id, id: Id) {
        val personas = getAuthorities().filterIsInstance<Persona>()

        // TODO: Add test
        val persona = personas.firstOrNull { it.authorityId == personaId && it.entityId == personaId }
            ?: throw IllegalArgumentException("Invalid persona id: $personaId")

        // TODO: Add test
        if(personas.count() == 1 && persona.authorityId == persona.entityId)
            throw IllegalArgumentException("Can't delete only persona from address book.")

        // TODO: Add test
        val previousAuthority = entityStore.getEntity(persona.authorityId, id) as? Authority
            ?: throw IllegalStateException("Attempt to delete non existent authority: personaId=$personaId, id=$id")

        entityStore.deleteEntity(personaId, id)
        callUpdateHandlers(previousAuthority, null)
    }
}