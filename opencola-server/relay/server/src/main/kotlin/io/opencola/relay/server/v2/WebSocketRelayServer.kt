package io.opencola.relay.server.v2

import io.opencola.relay.common.State.*

// TODO: Remove?
class WebSocketRelayServer(
    numChallengeBytes: Int = 32
) : Server(numChallengeBytes) {

    override suspend fun open() {
        state = Open
        openMutex.unlock()
    }
}