package opencola.core.storage

import io.ktor.util.collections.*
import mu.KotlinLogging
import opencola.core.config.NetworkConfig
import opencola.core.config.PeerConfig
import opencola.core.config.ServerConfig
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.security.Signator
import opencola.core.security.decodePublicKey
import java.net.URI
import java.nio.file.Path
import java.security.PublicKey

// TODO: Move ServerConfig to NetworkConfig?
class AddressBook(private val authority: Authority, storagePath: Path, signator: Signator, serverConfig: ServerConfig, networkConfig: NetworkConfig) {
    val logger = KotlinLogging.logger("AddressBook")

    private val activeTag = "active"
    private val entityStore = ExposedEntityStore(SQLiteDB(storagePath.resolve("address-book.db")).db, authority, signator)
    private val updateHandlers = ConcurrentList<(Authority) -> Unit>()

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

    // TODO: Change update handler to be (previousAuthority?, currentAuthority?) so it can generically handle add, update, delete
    fun addUpdateHandler(handler: (Authority) -> Unit){
        updateHandlers.add(handler)
    }

    fun removeUpdateHandler(handler: (Authority) -> Unit){
        updateHandlers.remove(handler)
    }

    fun updateAuthority(authority: Authority, suppressUpdateHandler: ((Authority) -> Unit)? = null) : Authority {
        if(entityStore.updateEntities(authority) != null) {
            val updatedAuthority = entityStore.getEntity(authority.authorityId, authority.entityId) as Authority

            updateHandlers.forEach {
                try {
                    if(it != suppressUpdateHandler)
                        it(updatedAuthority)
                } catch (e: Exception) {
                    logger.error { "Error calling update handler: $e" }
                }
            }

            return updatedAuthority
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

        entityStore.deleteEntity(authority.authorityId, id)
    }
}