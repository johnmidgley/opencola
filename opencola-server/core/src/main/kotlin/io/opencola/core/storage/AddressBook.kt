package io.opencola.core.storage

import io.ktor.util.collections.*
import mu.KotlinLogging
import io.opencola.core.config.NetworkConfig
import io.opencola.core.config.PeerConfig
import io.opencola.core.config.ServerConfig
import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import io.opencola.core.security.Signator
import io.opencola.core.security.decodePublicKey
import java.net.URI
import java.nio.file.Path
import java.security.PublicKey

// TODO: Move ServerConfig to NetworkConfig?
class AddressBook(private val authority: Authority, storagePath: Path, signator: Signator, serverConfig: ServerConfig, networkConfig: NetworkConfig) {
    val logger = KotlinLogging.logger("AddressBook")

    private val activeTag = "active"
    private val entityStore = ExposedEntityStore(SQLiteDB(storagePath.resolve("address-book.db")).db, authority, signator)
    private val updateHandlers = ConcurrentList<(Authority?, Authority?) -> Unit>()

    fun getPublicKey(authorityId: Id): PublicKey? {
        return if (authorityId == authority.authorityId)
            authority.publicKey
        else
            getAuthority(authorityId)?.publicKey
    }

    init {
        // TODO: Does something have to be updated here when public key is updatable?
        val addressBookAuthority = getAuthority(authority.authorityId) ?: updateAuthority(authority)

        if(addressBookAuthority.uri.toString().isBlank()) {
            // Fallback to local server address.
            // TODO: Is there a better place for this?
            addressBookAuthority.uri = URI("http://${serverConfig.host}:${serverConfig.port}")
            updateAuthority(addressBookAuthority)
        }

        importPeers(networkConfig.peers)
    }

    private fun importPeers(peers: List<PeerConfig>) {
        peers
            .forEach {
                logger.info { "Importing peer: $it" }
                val uri = URI("http://${it.host}")
                val tags = if (it.active) setOf(activeTag) else emptySet()
                val peerAuthority = getAuthority(Id.decode(it.id)) ?:
                    Authority(authority.authorityId, decodePublicKey(it.publicKey), uri, it.name)

                peerAuthority.name = it.name
                peerAuthority.tags = tags
                updateAuthority(peerAuthority)
            }
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

        if(entityStore.updateEntities(authority) != null) {
            val currentAuthority = entityStore.getEntity(authority.authorityId, authority.entityId) as Authority
            callUpdateHandlers(previousAuthority, currentAuthority, suppressUpdateHandler)
            return currentAuthority
        }

        // Nothing changed
        return authority
    }

    fun getAuthority(id: Id) : Authority? {
        return entityStore.getEntity(authority.authorityId, id) as Authority?
    }

    // TODO: Make isActive be a property of Authority and remove filter here (caller can do it)
    fun getAuthorities(filterActive: Boolean = false) : Set<Authority> {
        return entityStore.getEntities(setOf(authority.authorityId), emptySet())
            .filterIsInstance<Authority>()
            .filter { !filterActive || it.tags.contains(activeTag)}
            .toSet()
    }

    fun deleteAuthority(id: Id) {
        if(id == authority.authorityId)
            throw IllegalArgumentException("Can't delete root authority from address book.")

        val previousAuthority = entityStore.getEntity(authority.authorityId, authority.entityId) as Authority
        callUpdateHandlers(previousAuthority, null)
        entityStore.deleteEntity(authority.authorityId, id)
    }
}