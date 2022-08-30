package opencola.server.handlers

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import io.opencola.core.event.EventBus
import io.opencola.core.event.Events
import opencola.core.extensions.nullOrElse
import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import io.opencola.core.network.InviteToken
import io.opencola.core.network.Notification
import io.opencola.core.network.PeerEvent
import io.opencola.core.network.providers.zerotier.ZeroTierNetworkProvider
import io.opencola.core.security.Encryptor
import io.opencola.core.security.Signator
import io.opencola.core.security.decodePublicKey
import io.opencola.core.security.encode
import io.opencola.core.storage.AddressBook
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

fun updatePeer(authorityId: Id, addressBook: AddressBook, encryptor: Encryptor, eventBus: EventBus, peer: Peer) {
    logger.info { "Updating peer: $peer" }

    val peerAuthority = peer.toAuthority(authorityId, encryptor)
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

    if(peer.networkToken != null){
        if(peerAuthority.entityId != authorityId){
            throw IllegalArgumentException("Attempt to set networkToken for non root authority")
        }

        if(peer.networkToken != redactedNetworkToken) {
            if(!ZeroTierNetworkProvider.isNetworkTokenValid(peer.networkToken)){
                throw IllegalArgumentException("Network token provided is not valid: ${peer.networkToken}")
            }

            peerAuthority.networkToken = encryptor.encrypt(authorityId, peer.networkToken.toByteArray())
        }
    }

    val peerToUpdate = existingPeerAuthority ?: peerAuthority
    addressBook.updateAuthority(peerToUpdate)

    // TODO: Move out of here - doesn't belong
    if (existingPeerAuthority == null)
    // New peer has been added - request transactions
        eventBus.sendMessage(
            Events.PeerNotification.toString(),
            Notification(peerAuthority.entityId, PeerEvent.Added).encode()
        )
}

private fun removePeer(peerId: Id, addressBook: AddressBook) {
    logger.info { "Removing peer: $peerId" }
    val peer = addressBook.getAuthority(peerId)

    if(peer == null){
        logger.info { "No peer found - ignoring" }
        return
    }

    addressBook.deleteAuthority(peerId)
}

fun getInviteToken(authorityId: Id, addressBook: AddressBook, signator: Signator): String {
    val authority = addressBook.getAuthority(authorityId)
        ?: throw IllegalStateException("Root authority not found - can't generate invite token")
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
        null,
    )
}

