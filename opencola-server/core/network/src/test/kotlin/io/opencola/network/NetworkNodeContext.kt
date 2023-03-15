package io.opencola.network

import io.opencola.network.providers.MockNetworkProvider
import io.opencola.storage.MockAddressBook

class NetworkNodeContext(
    val addressBook: MockAddressBook,
    val provider: MockNetworkProvider,
    val networkNode: NetworkNode,
) {
}