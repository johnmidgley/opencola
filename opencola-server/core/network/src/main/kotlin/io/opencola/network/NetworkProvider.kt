package io.opencola.network

import io.opencola.model.Id
import io.opencola.network.message.SignedMessage
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import java.net.URI

typealias EventHandler = (ProviderEvent) -> Unit
typealias MessageHandler = (Id, Id, SignedMessage) -> Unit

interface NetworkProvider {
    fun setEventHandler(handler: EventHandler)
    fun setMessageHandler(handler: MessageHandler)

    fun start(waitUntilReady: Boolean = false)
    fun stop()

    fun getScheme() : String
    fun validateAddress(address: URI)

    // If a peer URI changes with the same provider, it will result in removePeer(oldPeer) addPeer(newPeer)
    fun addPeer(peer: AddressBookEntry)
    fun removePeer(peer: AddressBookEntry)

    fun sendMessage(from: PersonaAddressBookEntry, to: Set<AddressBookEntry>, signedMessage: SignedMessage)
    fun sendMessage(from: PersonaAddressBookEntry, to: AddressBookEntry, signedMessage: SignedMessage) {
        sendMessage(from, setOf(to), signedMessage)
    }
}