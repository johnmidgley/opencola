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

package io.opencola.network

import io.opencola.event.bus.EventBus
import io.opencola.event.MockEventBus
import io.opencola.model.Id
import io.opencola.network.NetworkNode.*
import io.opencola.network.message.Message
import io.opencola.network.providers.MockNetworkProvider
import io.opencola.security.keystore.KeyStore
import io.opencola.security.MockKeyStore
import io.opencola.security.Signator
import io.opencola.security.generateKeyPair
import io.opencola.storage.*
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentAddressedFileStore
import java.security.KeyPair

// TODO: Make closeable end use .use{ }?
class NetworkNodeContext(
    val keyStore: KeyStore = MockKeyStore(),
    val eventBus: EventBus = MockEventBus(),
    val contentBasedFileStore: ContentAddressedFileStore = MockContentAddressedFileStore(),
    val signator: Signator = Signator(keyStore),
    val addressBook: AddressBook = MockAddressBook(keyStore),
    val provider: MockNetworkProvider = MockNetworkProvider(addressBook, keyStore),
    val entityStore: EntityStore = MockEntityStore(signator, addressBook),
    val routes: List<Route> = getDefaultRoutes(entityStore, contentBasedFileStore),
    val networkNode: NetworkNode = NetworkNode(
        NetworkConfig(),
        routes,
        addressBook,
        eventBus,
    ).also { it.addProvider(provider) },
    autoStart: Boolean = true
) {
    init {
        if (autoStart) {
            start()
        }
    }

    data class Peer(val keyPair: KeyPair, val addressBookEntry: AddressBookEntry)

    val persona = addressBook.addPersona("Persona0")

    fun start() {
        eventBus.start()
        networkNode.start()
    }

    fun stop() {
        networkNode.stop()
        eventBus.stop()
    }


    fun addPeer(name: String, isActive: Boolean = true, keyPair: KeyPair = generateKeyPair()): Peer {
        return Peer(keyPair, addressBook.addPeer(persona.personaId, name, isActive, keyPair.public))
    }

    fun setRoute(route: Route) {
        if (networkNode.routes.any { it.messageClass == route.messageClass }) {
            networkNode.routes = networkNode.routes.map { if (it.messageClass == route.messageClass) route else it }
        } else {
            networkNode.routes += route
        }
    }

    fun handleMessage(peer: Peer, to: Id, message: Message) {
        provider.handleMessage(peer.addressBookEntry.entityId, to, message)
    }
}



