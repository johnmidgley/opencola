package io.opencola.network

import io.opencola.event.EventBus
import io.opencola.event.MockEventBus
import io.opencola.network.providers.MockNetworkProvider
import io.opencola.security.KeyStore
import io.opencola.security.MockKeyStore
import io.opencola.security.Signator
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.MockAddressBook
import io.opencola.storage.addPersona

class NetworkNodeContext(
    val keyStore: KeyStore = MockKeyStore(),
    val addressBook: AddressBook = MockAddressBook(keyStore),
    val eventBus: EventBus = MockEventBus(),
    val routes: List<Route> = listOf(Route("ping") { _, _, _ -> TODO("Fix") }),
    val router: RequestRouter = RequestRouter(addressBook, routes),
    val provider: MockNetworkProvider = MockNetworkProvider(addressBook, keyStore),
    val signator: Signator = Signator(keyStore),
    val networkNode: NetworkNode = NetworkNode(NetworkConfig(), router, addressBook, eventBus, signator).also { it.addProvider(provider) },
) {
    val persona = addressBook.addPersona("Persona0")
}