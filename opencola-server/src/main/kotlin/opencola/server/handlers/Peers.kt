package opencola.server.handlers

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.security.decodePublicKey
import opencola.core.security.encode
import opencola.core.storage.AddressBook
import java.net.URI
import java.security.PublicKey

private val logger = KotlinLogging.logger("PeerHandler")

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

    fun toAuthority(authorityId: Id) : Authority {
        return Authority(
            authorityId,
            decodePublicKey(publicKey),
            URI(address),
            name,
            imageUri = imageUri.nullOrElse { URI(it) },
            tags = if(isActive) setOf("active") else null
        )
    }
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

fun deletePeer(addressBook: AddressBook, peerId: Id){
    logger.info { "Deleting Peer: $peerId" }
    addressBook.deleteAuthority(peerId)
}

fun updatePeer(authority: Authority, addressBook: AddressBook, peer: Peer){
    logger.info { "Updating Peer: $peer"}

    val updateAuthority = peer.toAuthority(authority.authorityId)
    val peerAuthority = addressBook.getAuthority(Id.decode(peer.id)) ?: updateAuthority

    peerAuthority.name = updateAuthority.name
    peerAuthority.publicKey = updateAuthority.publicKey
    peerAuthority.uri = updateAuthority.uri
    peerAuthority.imageUri = updateAuthority.imageUri
    peerAuthority.tags = updateAuthority.tags

    addressBook.updateAuthority(peerAuthority)
}