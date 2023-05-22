package io.opencola.network

import io.opencola.event.EventBus
import io.opencola.event.MockEventBus
import io.opencola.model.Id
import io.opencola.network.NetworkNode.*
import io.opencola.network.message.MessageEnvelope
import io.opencola.network.message.SignedMessage
import io.opencola.network.message.UnsignedMessage
import io.opencola.network.providers.MockNetworkProvider
import io.opencola.security.KeyStore
import io.opencola.security.MockKeyStore
import io.opencola.security.Signator
import io.opencola.security.generateKeyPair
import io.opencola.storage.*
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentBasedFileStore
import java.security.KeyPair

class NetworkNodeContext(
    val keyStore: KeyStore = MockKeyStore(),
    val eventBus: EventBus = MockEventBus(),
    val contentBasedFileStore: ContentBasedFileStore = MockContentBasedFileStore(),
    val signator: Signator = Signator(keyStore),
    val addressBook: AddressBook = MockAddressBook(keyStore),
    val provider: MockNetworkProvider = MockNetworkProvider(addressBook, keyStore),
    val entityStore: EntityStore = MockEntityStore(signator, addressBook),
    val routes: List<Route> = getDefaultRoutes(entityStore, contentBasedFileStore),
    val networkNode: NetworkNode = NetworkNode(NetworkConfig(), routes, addressBook, eventBus, signator).also { it.addProvider(provider) },
) {
    data class Peer(val keyPair: KeyPair, val addressBookEntry: AddressBookEntry)
    val persona = addressBook.addPersona("Persona0")

    fun addPeer(name: String, isActive: Boolean = true): Peer {
        return generateKeyPair().let { Peer(it, addressBook.addPeer(persona.personaId, name, isActive, it.public)) }
    }

    fun getEncodedEnvelope(from: KeyPair, to: Id, unsignedMessage: UnsignedMessage): ByteArray {
        val fromId = Id.ofPublicKey(from.public)

        // Add private key to keystore needed for signing
        keyStore.addKeyPair(fromId.toString(), from)

       return MessageEnvelope(to, SignedMessage(fromId, unsignedMessage, signator)).encode(null)
    }

    fun setRoute(route: Route) {
        if(networkNode.routes.any { it.messageType == route.messageType }) {
            networkNode.routes = networkNode.routes.map {
                if (it.messageType == route.messageType)
                    route
                else
                    it
            }
        } else {
            networkNode.routes = networkNode.routes + route
        }
    }

    fun handleMessage(peer: Peer, to: Id, unsignedMessage: UnsignedMessage) {
        val encodedEnvelope = getEncodedEnvelope(peer.keyPair, to, unsignedMessage)
        provider.handleMessage(encodedEnvelope, false)
    }
}



