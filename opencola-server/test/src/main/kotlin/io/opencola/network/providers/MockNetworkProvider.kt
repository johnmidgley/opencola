package io.opencola.network.providers

import io.opencola.network.AbstractNetworkProvider
import io.opencola.network.Request
import io.opencola.network.Response
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
    var onSendRequest: ((PersonaAddressBookEntry, AddressBookEntry, Request) -> Response?) ?= null

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

    override fun sendRequest(from: PersonaAddressBookEntry, to: AddressBookEntry, request: Request): Response? {
        return onSendRequest?.let { it(from, to, request) }
            ?: throw IllegalStateException("onSendRequest not set")
    }
}