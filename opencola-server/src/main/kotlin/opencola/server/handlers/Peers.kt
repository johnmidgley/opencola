package opencola.server.handlers

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.security.Encryptor
import opencola.core.security.decodePublicKey
import opencola.core.security.encode
import opencola.core.storage.AddressBook
import java.net.URI
import java.security.PublicKey

private val logger = KotlinLogging.logger("PeerHandler")
private const val redactedNetworkToken = "##########"

@Serializable
data class Peer(
    val id: String,
    val name: String,
    val publicKey: String,
    val address: String,
    val imageUri: String?,
    val isActive: Boolean,
    val networkToken: String?,
) {
    constructor(id: Id, name: String, publicKey: PublicKey, address: URI, imageUri: URI?, isActive: Boolean, networkToken: String?) :
            this(
                id.toString(),
                name,
                publicKey.encode(),
                address.toString(),
                imageUri?.toString(),
                isActive,
                networkToken,
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
        Peer(it.entityId, it.name!!, it.publicKey!!, it.uri!!, it.imageUri, addressBook.isAuthorityActive(it), it.networkToken.nullOrElse { redactedNetworkToken })
    }

    return PeersResult(authority, null, peers)
}

fun deletePeer(addressBook: AddressBook, peerId: Id){
    logger.info { "Deleting Peer: $peerId" }
    addressBook.deleteAuthority(peerId)
}

// TODO: Should be changed to updateAuthority, because root is updated here too.
fun updatePeer(authority: Authority, addressBook: AddressBook, encryptor: Encryptor, peer: Peer){
    logger.info { "Updating Peer: $peer"}

    val updateAuthority = peer.toAuthority(authority.authorityId)
    val peerAuthority = addressBook.getAuthority(Id.decode(peer.id)) ?: updateAuthority

    if(updateAuthority.publicKey != peerAuthority.publicKey){
        throw NotImplementedError("Updating publicKey is not currently implemented")
    }

    peerAuthority.name = updateAuthority.name
    peerAuthority.publicKey = updateAuthority.publicKey
    peerAuthority.uri = updateAuthority.uri
    peerAuthority.imageUri = updateAuthority.imageUri
    peerAuthority.tags = updateAuthority.tags

    if(peer.networkToken != null){
        if(updateAuthority.entityId != authority.authorityId){
            throw IllegalArgumentException("Attempt to set networkToken for not root authority")
        }

        if(peer.networkToken != redactedNetworkToken) {
            // TODO: Test token with ZT before saving
            peerAuthority.networkToken = encryptor.encrypt(authority.authorityId, peer.networkToken.toByteArray())
        }
    }

    addressBook.updateAuthority(peerAuthority)
}