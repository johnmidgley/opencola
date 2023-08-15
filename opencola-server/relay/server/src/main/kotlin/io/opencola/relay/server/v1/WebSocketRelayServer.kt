package io.opencola.relay.server.v1

import io.opencola.relay.common.State.*
import io.opencola.relay.common.message.v1.Envelope

class WebSocketRelayServer(
    numChallengeBytes: Int = 32
) : Server(numChallengeBytes) {
    override suspend fun open() {
        state = Open
        openMutex.unlock()
    }

    override fun decodePayload(payload: ByteArray): Envelope {
        return Envelope.decode(payload)
    }
}