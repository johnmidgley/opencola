package io.opencola.network.providers

import io.opencola.network.AbstractNetworkProvider
import io.opencola.network.message.SignedMessage
import io.opencola.security.Encryptor
import io.opencola.security.KeyStore
import io.opencola.security.Signator
import io.opencola.storage.AddressBook
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.PersonaAddressBookEntry
import java.net.URI

class MockNetworkProvider(addressBook: AddressBook,
                          keyStore: KeyStore
) : AbstractNetworkProvider(addressBook, Signator(keyStore), Encryptor(keyStore)) {
    private val logger = mu.KotlinLogging.logger("MockNetworkProvider")
    var onSendMessage: ((PersonaAddressBookEntry, AddressBookEntry, SignedMessage) -> Unit)? = null

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

    override fun sendMessage(from: PersonaAddressBookEntry, to: AddressBookEntry, signedMessage: SignedMessage) {
        onSendMessage?.let{
            it(from, to, signedMessage)
        } ?: throw IllegalStateException("onSendRequest not set")
    }
}