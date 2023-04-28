package io.opencola.network

import io.opencola.event.EventBus
import io.opencola.event.MockEventBus
import io.opencola.model.Id
import io.opencola.network.providers.MockNetworkProvider
import io.opencola.security.KeyStore
import io.opencola.security.MockKeyStore
import io.opencola.security.sign
import io.opencola.storage.AddressBook
import io.opencola.storage.MockAddressBook
import kotlinx.serialization.encodeToString
import java.security.PrivateKey
import kotlinx.serialization.json.Json

class NetworkNodeContext(
    val keyStore: KeyStore = MockKeyStore(),
    val addressBook: AddressBook = MockAddressBook(keyStore),
    val eventBus: EventBus = MockEventBus(),
    val routes: List<Route> = listOf(Route(Request.Method.GET,"/ping") { _, _, _ -> Response(200, "pong") }),
    val router: RequestRouter = RequestRouter(addressBook, routes),
    val provider: MockNetworkProvider = MockNetworkProvider(addressBook, keyStore),
    val networkNode: NetworkNode = NetworkNode(NetworkConfig(), router, addressBook, eventBus).also { it.addProvider(provider) },
)

// TODO: This should probably be the actual code used in main - but it depends on the address book.
fun getEncodedEnvelope(fromId: Id, fromPrivateKey: PrivateKey, toId: Id, request: Request): ByteArray {
    val messageBytes = Json.encodeToString(request).toByteArray()
    val message = Message(fromId, messageBytes, sign(fromPrivateKey, messageBytes).bytes)
    return MessageEnvelope(toId, message).encode()
}