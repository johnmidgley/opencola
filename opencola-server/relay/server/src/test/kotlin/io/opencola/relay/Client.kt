package io.opencola.relay

import io.opencola.relay.client.AbstractClient
import io.opencola.relay.common.retryExponentialBackoff
import io.opencola.relay.client.v1.WebSocketClient as WebSocketClientV1
import io.opencola.relay.client.v2.WebSocketClient as WebSocketClientV2
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
    retryPolicy: (Int) -> Long = retryExponentialBackoff(),
    relayServerUri: URI = localRelayServerUri,
): AbstractClient {
    return when (clientType) {
        ClientType.V1 -> WebSocketClientV1(
            relayServerUri,
            keyPair,
            name,
            requestTimeoutInMilliseconds,
            retryPolicy = retryPolicy
        )

        ClientType.V2 -> WebSocketClientV2(
            relayServerUri,
            keyPair,
            name,
            requestTimeoutInMilliseconds,
            retryPolicy = retryPolicy
        )
    }
}