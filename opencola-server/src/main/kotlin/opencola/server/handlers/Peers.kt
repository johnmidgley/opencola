package opencola.server.handlers

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.network.InviteToken
import opencola.core.network.NetworkNode
import opencola.core.security.Encryptor
import opencola.core.security.Signator
import opencola.core.security.decodePublicKey
import opencola.core.security.encode
import opencola.core.storage.AddressBook
import java.net.URI
import java.security.PublicKey

private val logger = KotlinLogging.logger("PeerHandler")
const val redactedNetworkToken = "##########"

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

    fun toAuthority(authorityId: Id, encryptor: Encryptor) : Authority {
        return Authority(
            authorityId,
            decodePublicKey(publicKey),
            URI(address),
            name,
            imageUri = imageUri.nullOrElse { URI(it) },
        ).also { authority ->
            authority.setActive(isActive)
            authority.networkToken = networkToken.nullOrElse { encryptor.encrypt(authorityId, it.toByteArray()) }
        }
    }
}

@Serializable
data class TokenRequest(val token: String)

@Serializable
data class PeersResult(val authorityId: String, val pagingToken: String?, val results: List<Peer>) {
    constructor(authority: Authority, pagingToken: String?, peers: List<Peer>) :
            this(authority.entityId.toString(), null, peers)
}

fun getPeers(authority: Authority, addressBook: AddressBook): PeersResult {
    val peers = addressBook.getAuthorities().map {
        // TODO: Make name, public key and uri required for authority
        Peer(
            it.entityId,
            it.name!!,
            it.publicKey!!,
            it.uri!!,
            it.imageUri,
            addressBook.isAuthorityActive(it),
            it.networkToken.nullOrElse { redactedNetworkToken },
        )
    }

    return PeersResult(authority, null, peers)
}

fun deletePeer(addressBook: AddressBook, peerId: Id) {
    logger.info { "Deleting Peer: $peerId" }
    addressBook.deleteAuthority(peerId)
}

fun updatePeer(networkNode: NetworkNode, peer: Peer) {
    networkNode.updatePeer(peer)
}

fun getInviteToken(authorityId: Id, addressBook: AddressBook, signator: Signator): String {
    val authority = addressBook.getAuthority(authorityId)
        ?: throw IllegalStateException("Root authority not found - can't generate invite token")
    return InviteToken.fromAuthority(authority).encodeBase58(signator)
}


fun inviteTokenToPeer(networkNode: NetworkNode, token: String): Peer {
    return networkNode.inviteTokenToPeer(token)
}

