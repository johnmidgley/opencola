package io.opencola.relay.server

import io.opencola.relay.common.State.*

class WebSocketRelayServer(
    numChallengeBytes: Int = 32
) : AbstractRelayServer(numChallengeBytes) {
    override suspend fun open() {
        state = Open
        openMutex.unlock()
    }
}