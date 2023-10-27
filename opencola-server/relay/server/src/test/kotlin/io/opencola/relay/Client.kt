package io.opencola.relay

import io.opencola.relay.client.AbstractClient
import io.opencola.relay.client.v1.WebSocketClient
import io.opencola.security.generateKeyPair
import java.net.URI
import java.security.KeyPair

enum class ClientType {
    V1,
    V2
}

fun getClient(
    clientType: ClientType,
    name: String,
    keyPair: KeyPair = generateKeyPair(),
    requestTimeoutInMilliseconds: Long = 5000,
    relayServerUri: URI = localRelayServerUri,
): AbstractClient {
    return when (clientType) {
        ClientType.V1 -> WebSocketClient(relayServerUri, keyPair, name, requestTimeoutInMilliseconds)
        ClientType.V2 -> io.opencola.relay.client.v2.WebSocketClient(
            relayServerUri,
            keyPair,
            name,
            requestTimeoutInMilliseconds
        )
    }
}