/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.network.providers

import io.opencola.network.message.Message
import io.opencola.security.keystore.KeyStore
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