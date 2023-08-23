package io.opencola.network.providers

import io.opencola.network.message.Message
import io.opencola.security.KeyStore
import io.opencola.security.Signator
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import java.net.URI

class MockNetworkProvider(addressBook: AddressBook,
                          keyStore: KeyStore
) : AbstractNetworkProvider(addressBook, Signator(keyStore)) {
    private val logger = mu.KotlinLogging.logger("MockNetworkProvider")
    var onSendMessage: ((PersonaAddressBookEntry, AddressBookEntry, Message) -> Unit)? = null


    override fun start(waitUntilReady: Boolean) {
        logger.info { "Starting MockNetworkProvider" }
        this.started = true
    }

    override fun stop() {
        logger.info { "Stopping MockNetworkProvider" }
        this.started = false
    }

    override fun getScheme(): String {
        return "mock"
    }

    override fun validateAddress(address: URI) {
        if(address.scheme != getScheme())
            throw IllegalArgumentException("Invalid scheme: ${address.scheme}")
    }

    override fun addPeer(peer: AddressBookEntry) {
        logger.info { "Adding peer: $peer" }
    }

    override fun removePeer(peer: AddressBookEntry) {
        logger.info { "Removing peer: $peer" }
    }

    override fun sendMessage(from: PersonaAddressBookEntry, to: AddressBookEntry, message: Message) {
        onSendMessage?.let{
            it(from, to, message)
        } ?: throw IllegalStateException("onSendRequest not set")
    }

    override fun sendMessage(from: PersonaAddressBookEntry, to: Set<AddressBookEntry>, message: Message) {
        to.forEach { sendMessage(from, it, message) }
    }

    override fun handleMessage(envelopeBytes: ByteArray, context: ProviderContext?) {
        throw NotImplementedError("MockNetworkProvider does not support receiving messages")
    }
}