package io.opencola.storage

import mu.KotlinLogging
import io.opencola.model.Authority
import io.opencola.model.Id
import io.opencola.model.Persona
import io.opencola.security.KeyStore
import io.opencola.security.PublicKeyProvider
import io.opencola.security.Signator
import io.opencola.util.trim
import java.lang.StringBuilder
import java.nio.file.Path
import java.security.PublicKey
import java.util.concurrent.CopyOnWriteArrayList

// TODO: Move ServerConfig to NetworkConfig?
class AddressBook(private val storagePath: Path, private val keyStore: KeyStore) : PublicKeyProvider<Id> {
    class KeyStorePublicKeyProvider(private val keyStore: KeyStore) : PublicKeyProvider<Id> {
        override fun getPublicKey(alias: Id): PublicKey? {
            return keyStore.getPublicKey(alias.toString())
        }
    }

    val logger = KotlinLogging.logger("AddressBook")

    private val activeTag = "active"
    private val entityStore = ExposedEntityStore(SQLiteDB(storagePath.resolve("address-book.db")).db, Signator(keyStore), KeyStorePublicKeyProvider(keyStore))
    private val updateHandlers = CopyOnWriteArrayList<(Authority?, Authority?) -> Unit>()

    override fun toString(): String {
        val stringBuilder = StringBuilder("AddressBook($storagePath){\n")
        val (personas, peers) = getAuthorities().partition { it is Persona }

        personas.forEach { stringBuilder.appendLine("\tPersona: $it") }
        peers.forEach { stringBuilder.appendLine("\tPeer: $it") }
        stringBuilder.appendLine("}")

        return stringBuilder.toString()
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
        // TODO: When publicKey update is allowed, need to force public keys to be same across personas

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
        if(personas.count() == 1 && personaId == id)
            throw IllegalArgumentException("Can't delete only persona from address book.")

        // TODO: Add test
        val previousAuthority = entityStore.getEntity(persona.authorityId, id) as? Authority
            ?: throw IllegalStateException("Attempt to delete non existent authority: personaId=$personaId, id=$id")

        entityStore.deleteEntity(personaId, id)
        callUpdateHandlers(previousAuthority, null)
    }

    override fun getPublicKey(alias: Id): PublicKey? {
        return getAuthorities().firstOrNull { it.entityId == alias && it.publicKey != null }?.publicKey
    }
}