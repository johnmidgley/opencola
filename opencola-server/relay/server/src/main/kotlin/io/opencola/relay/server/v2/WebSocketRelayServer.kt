package io.opencola.relay.server.v2

import io.opencola.relay.common.State.*
import java.net.URI

// TODO: Remove?
class WebSocketRelayServer(
    address: URI,
    numChallengeBytes: Int = 32
) : Server(address, numChallengeBytes) {
    override suspend fun open() {
        state = Open
        openMutex.unlock()
    }
}