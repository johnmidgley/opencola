/*
 * Copyright 2024-2026 OpenCola
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

import io.opencola.model.Id
import io.opencola.network.message.Message
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import java.net.URI

typealias EventHandler = (ProviderEvent) -> Unit
typealias MessageHandler = (Id, Id, Message) -> Unit

// TODO: remove 's' io.opencola.network.providers -> io.opencola.network.provider

interface NetworkProvider {
    // Set an event handler that should be called for any events raised by the provider (e.g. NO_PENDING_MESSAGES).
    fun setEventHandler(handler: EventHandler)

    // Set a message handler that is called whenever a message is received by the provider
    fun setMessageHandler(handler: MessageHandler)

    // Start the provider
    fun start(waitUntilReady: Boolean = false)

    // Stop the provider
    fun stop()

    // Get the scheme of URIs that the provider handlers (e.g. http or ocr)
    fun getScheme() : String

    // Validate an address that the provider should handle. Can check if the remote server is valid.
    fun validateAddress(address: URI)

    // Inform the provider tha a peer has been added, which may add a new remote connection.
    fun addPeer(peer: AddressBookEntry)

    // Inform the provider that a peer has been removed, which may remove a remote connection
    fun removePeer(peer: AddressBookEntry)

    // Send a message from a persona to a set of peers.
    fun sendMessage(from: PersonaAddressBookEntry, to: Set<AddressBookEntry>, message: Message)
    fun sendMessage(from: PersonaAddressBookEntry, to: AddressBookEntry, message: Message) {
        sendMessage(from, setOf(to), message)
    }
}