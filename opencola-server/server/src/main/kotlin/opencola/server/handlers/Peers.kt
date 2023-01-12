package opencola.server.handlers

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import io.opencola.core.event.EventBus
import io.opencola.core.event.Events
import io.opencola.core.extensions.nullOrElse
import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import io.opencola.core.network.InviteToken
import io.opencola.core.network.NetworkNode
import io.opencola.core.network.Notification
import io.opencola.core.network.PeerEvent
import io.opencola.core.security.Signator
import io.opencola.core.security.decodePublicKey
import io.opencola.core.security.encode
import io.opencola.core.storage.AddressBook
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
    val isActive: Boolean,
) {
    constructor(id: Id, name: String, publicKey: PublicKey, address: URI, imageUri: URI?, isActive: Boolean) :
            this(
                id.toString(),
                name,
                publicKey.encode(),
                address.toString(),
                imageUri?.toString(),
                isActive,
            )

    fun toAuthority(authorityId: Id) : Authority {
        return Authority(
            authorityId,
            decodePublicKey(publicKey),
            URI(address),
            name,
            imageUri = imageUri.nullOrElse { URI(it) },
        ).also { authority ->
            authority.setActive(isActive)
        }
    }
}

@Serializable
data class TokenRequest(val token: String)

@Serializable
data class PeersResult(val authorityId: String, val pagingToken: String?, val results: List<Peer>) {
    constructor(authority: Authority, pagingToken: String?, peers: List<Peer>) :
            this(authority.entityId.toString(), pagingToken, peers)
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
        )
    }

    return PeersResult(authority, null, peers)
}

fun deletePeer(addressBook: AddressBook, peerId: Id) {
    logger.info { "Deleting Peer: $peerId" }
    addressBook.deleteAuthority(peerId)
}

fun updatePeer(authorityId: Id, addressBook: AddressBook, networkNode: NetworkNode, eventBus: EventBus, peer: Peer) {
    logger.info { "Updating peer: $peer" }

    if(Id.ofPublicKey(decodePublicKey(peer.publicKey)) != Id.decode(peer.id))
        throw IllegalArgumentException("Public key update not supported yet")

    val peerAuthority = peer.toAuthority(authorityId)
    val existingPeerAuthority = addressBook.getAuthority(peerAuthority.entityId)

    if(existingPeerAuthority != null) {
        logger.info { "Found existing peer - updating" }

        if(existingPeerAuthority.publicKey != peerAuthority.publicKey){
            throw NotImplementedError("Updating publicKey is not currently implemented")
        }

        // TODO: Should there be a general way to do this? Add an update method to Entity or Authority?
        existingPeerAuthority.name = peerAuthority.name
        existingPeerAuthority.publicKey = peerAuthority.publicKey
        existingPeerAuthority.uri = peerAuthority.uri
        existingPeerAuthority.imageUri = peerAuthority.imageUri
        existingPeerAuthority.tags = peerAuthority.tags
        existingPeerAuthority.networkToken = peerAuthority.networkToken
    }

    val peerToUpdate = existingPeerAuthority ?: peerAuthority

    if(peerToUpdate.uri == null)
        throw IllegalArgumentException("No URI specified for peer: ${peerToUpdate.entityId}")
    else
        networkNode.validatePeerAddress(peerToUpdate.uri!!)

    addressBook.updateAuthority(peerToUpdate)

    // TODO: Move out of here - doesn't belong
    if (existingPeerAuthority == null)
    // New peer has been added - request transactions
        eventBus.sendMessage(
            Events.PeerNotification.toString(),
            Notification(peerAuthority.entityId, PeerEvent.Added).encode()
        )
}

fun getInviteToken(authorityId: Id, addressBook: AddressBook, networkNode: NetworkNode, signator: Signator): String {
    val authority = addressBook.getAuthority(authorityId)
        ?: throw IllegalStateException("Root authority not found - can't generate invite token")
    val address = authority.uri ?: throw IllegalArgumentException("Can't get invite token for peer without address")
    networkNode.validatePeerAddress(address)
    return InviteToken.fromAuthority(authority).encodeBase58(signator)
}

fun inviteTokenToPeer(authorityId: Id, inviteToken: String): Peer {
    val decodedInviteToken = InviteToken.decodeBase58(inviteToken)
    val imageUri = if(decodedInviteToken.imageUri.toString().isBlank()) null else decodedInviteToken.imageUri

    if(decodedInviteToken.authorityId == authorityId)
        throw IllegalArgumentException("You can't invite yourself (┛ಠ_ಠ)┛彡┻━┻")

    return Peer(
        decodedInviteToken.authorityId,
        decodedInviteToken.name,
        decodedInviteToken.publicKey,
        decodedInviteToken.address,
        imageUri,
        true,
    )
}

