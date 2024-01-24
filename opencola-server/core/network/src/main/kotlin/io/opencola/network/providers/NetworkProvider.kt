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