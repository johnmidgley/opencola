package io.opencola.relay.client.v1

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.relay.common.SocketSession
import io.opencola.relay.common.StandardSocketSession
import io.opencola.relay.common.retryExponentialBackoff
import kotlinx.coroutines.Dispatchers
import java.net.URI
import java.security.KeyPair

class StandardSocketClient(
    uri: URI,
    keyPair: KeyPair,
    name: String? = null,
    requestTimeoutMilliseconds: Long = 10000,
    retryPolicy: (Int) -> Long = retryExponentialBackoff(),
) : AbstractClient(uri, keyPair, name, requestTimeoutMilliseconds, retryPolicy) {
    override suspend fun getSocketSession(): SocketSession {
        return StandardSocketSession(aSocket(selectorManager).tcp().connect(hostname, port = port))
    }

    companion object {
        private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    }
}