package opencola.core.storage

import mu.KotlinLogging
import opencola.core.config.NetworkConfig
import opencola.core.extensions.hexStringToByteArray
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.model.Peer
import opencola.core.security.Signator
import opencola.core.security.publicKeyFromBytes
import java.net.URI
import java.nio.file.Path
import java.security.PublicKey

// TODO: Back by entity store
class AddressBook(private val authority: Authority, storagePath: Path, signator: Signator, networkConfig: NetworkConfig) {
    val logger = KotlinLogging.logger("AddressBook")

    private val activeTag = "active"
    private val entityStore = ExposedEntityStore(SQLiteDB(storagePath.resolve("address-book.db")).db, authority, signator)

    val peers: List<Peer>
        get() {
            return getAuthorities()
                .map { Peer(it.entityId, it.publicKey!!, it.name!!, it.uri?.authority ?: "", it.tags.contains(activeTag) ) }
        }


    fun getPublicKey(authorityId: Id): PublicKey? {
        return if (authorityId == authority.authorityId)
            authority.publicKey
        else
            peers.firstOrNull{ it.id == authorityId }?.publicKey
    }

    init {
        val peers = networkConfig.peers.map {
            // TODO: Remove Peer - just pass config
            Peer(Id.fromHexString(it.id), publicKeyFromBytes(it.publicKey.hexStringToByteArray()), it.name, it.host, it.active)
        }

        // TODO: Does something have to be updated here when public key is updatable?
        if(getAuthority(authority.authorityId) == null)
            putAuthority(authority)

        importPeers(peers)
    }

    private fun importPeers(peers: List<Peer>) {
        peers
            .forEach {
                if (getAuthority(it.id) != null)
                    logger.warn { "Ignoring existing peer: $it" }
                else {
                    logger.info { "Importing peer: $it" }
                    val tags = if (it.active) setOf(activeTag) else null
                    putAuthority(
                        Authority(
                            authority.authorityId,
                            it.publicKey,
                            URI("http://${it.host}"),
                            it.name,
                            tags = tags
                        )
                    )
                }
            }
    }

    fun putAuthority(authority: Authority) : Authority {
        entityStore.updateEntities(authority)
        return authority
    }

    fun getAuthority(id: Id) : Authority? {
        return if(id == authority.authorityId)
            authority
        else
            entityStore.getEntity(authority.authorityId, id) as Authority?
    }

    fun getAuthorities(filterActive: Boolean = false) : Set<Authority> {
        return entityStore.getEntities(emptyList(), emptyList())
            .filterIsInstance<Authority>()
            .filter { !filterActive || it.tags.contains(activeTag)}
            .toSet()
    }
}