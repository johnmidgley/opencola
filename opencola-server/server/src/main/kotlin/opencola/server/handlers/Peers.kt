package opencola.server.handlers

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.util.nullOrElse
import io.opencola.model.Authority
import io.opencola.model.Id
import io.opencola.network.InviteToken
import io.opencola.network.NetworkNode
import io.opencola.network.Notification
import io.opencola.network.PeerEvent
import io.opencola.security.Signator
import io.opencola.security.decodePublicKey
import io.opencola.security.encode
import io.opencola.storage.AddressBook
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.PersonaAddressBookEntry
import io.opencola.util.blankToNull
import java.net.URI
import java.security.PublicKey

private val logger = KotlinLogging.logger("PeerHandler")

@Serializable
// TODO: Add personaId to Peer, as peer is tied to a persona
// Move to Model
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
    constructor(personaId: Id, pagingToken: String?, peers: List<Peer>) :
            this(personaId.toString(), pagingToken, peers)
}

fun getPeers(persona: PersonaAddressBookEntry, addressBook: AddressBook): PeersResult {
    val peers = addressBook.getEntries()
        .filter{ it !is PersonaAddressBookEntry }
        .map {
        // TODO: Make name, public key and uri required for authority
        Peer(
            it.entityId,
            it.name,
            it.publicKey,
            it.address,
            it.imageUri,
            it.isActive
        )
    }

    return PeersResult(persona.personaId, null, peers)
}

fun deletePeer(addressBook: AddressBook, personaId: Id, peerId: Id) {
    logger.info { "Deleting Peer: personaId = $personaId, peerId = $peerId" }
    require(addressBook.getEntry(personaId, peerId) !is PersonaAddressBookEntry)
    addressBook.deleteEntry(personaId, peerId)
}

// TODO: Split out Create and Update
fun updatePeer(authorityId: Id, addressBook: AddressBook, networkNode: NetworkNode, eventBus: EventBus, peer: Peer) {
    logger.info { "Updating peer: $peer" }

    val existingPeerAuthority = addressBook.getEntry(authorityId, Id.decode(peer.id))
    require(existingPeerAuthority !is PersonaAddressBookEntry)

    if(Id.ofPublicKey(decodePublicKey(peer.publicKey)) != Id.decode(peer.id))
        throw IllegalArgumentException("Public key update not supported yet")

    val peerAddressBookEntry = AddressBookEntry(
        authorityId,
        Id.decode(peer.id),
        peer.name,
        decodePublicKey(peer.publicKey),
        URI(peer.address),
        peer.imageUri.blankToNull()?.let { URI(it) },
        peer.isActive
    )

    networkNode.validatePeerAddress(peerAddressBookEntry.address)
    addressBook.updateEntry(peerAddressBookEntry)

    // TODO: Move out of here - doesn't belong
    if (existingPeerAuthority == null)
    // New peer has been added - request transactions
        eventBus.sendMessage(
            Events.PeerNotification.toString(),
            Notification(peerAddressBookEntry.entityId, PeerEvent.Added).encode()
        )
}

fun getInviteToken(personaId: Id, addressBook: AddressBook, networkNode: NetworkNode, signator: Signator): String {
    val persona = addressBook.getEntry(personaId, personaId) as? PersonaAddressBookEntry
        ?: throw IllegalStateException("Persona not found - can't generate invite token")
    networkNode.validatePeerAddress(persona.address)
    return InviteToken.fromAddressBookEntry(persona).encodeBase58(signator)
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

