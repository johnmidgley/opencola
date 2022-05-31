package opencola.server.handlers

import kotlinx.serialization.Serializable
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.security.encode
import opencola.core.storage.AddressBook
import java.net.URI
import java.security.PublicKey

@Serializable
data class Peer(
    val id: String,
    val name: String,
    val publicKey: String,
    val address: String,
    val imageUri: String?,
    val isActive: Boolean
) {
    constructor(id: Id, name: String, publicKey: PublicKey, address: URI, imageUri: URI?, isActive: Boolean) :
            this(
                id.toString(),
                name,
                publicKey.encode(),
                address.toString(),
                imageUri?.toString(),
                isActive
            )
}

@Serializable
data class PeersResult(val authorityId: String, val pagingToken: String?, val results: List<Peer>) {
    constructor(authority: Authority, pagingToken: String?, peers: List<Peer>) :
            this(authority.entityId.toString(), null, peers)
}

fun getPeers(authority: Authority, addressBook: AddressBook): PeersResult {
    val peers = addressBook.getAuthorities().map {
        // TODO: Make name, public key and uri required for authority
        Peer(it.entityId, it.name!!, it.publicKey!!, it.uri!!, it.imageUri, addressBook.isAuthorityActive(it))
    }

    return PeersResult(authority, null, peers)
}