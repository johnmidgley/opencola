package io.opencola.network.providers

import io.opencola.model.Id
import io.opencola.network.message.Message
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import java.net.URI

typealias EventHandler = (ProviderEvent) -> Unit
typealias MessageHandler = (Id, Id, Message) -> Unit

// TODO: remove 's' io.opencola.network.providers -> io.opencola.network.provider

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

    fun sendMessage(from: PersonaAddressBookEntry, to: Set<AddressBookEntry>, message: Message)
    fun sendMessage(from: PersonaAddressBookEntry, to: AddressBookEntry, message: Message) {
        sendMessage(from, setOf(to), message)
    }
}