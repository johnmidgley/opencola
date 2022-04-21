package opencola.core.storage

import opencola.core.config.NetworkConfig
import opencola.core.extensions.hexStringToByteArray
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.model.Peer
import opencola.core.security.publicKeyFromBytes
import java.security.PublicKey

// TODO: Back by entity store
class AddressBook(private val authority: Authority, networkConfig: NetworkConfig) {
    val peers = networkConfig.peers.map {
        Peer(Id.fromHexString(it.id), publicKeyFromBytes(it.publicKey.hexStringToByteArray()), it.name, it.host, it.active)
    }

    fun getPublicKey(authorityId: Id): PublicKey? {
        return if (authorityId == authority.authorityId)
            authority.publicKey
        else
            peers.firstOrNull{ it.id == authorityId }?.publicKey
    }
}