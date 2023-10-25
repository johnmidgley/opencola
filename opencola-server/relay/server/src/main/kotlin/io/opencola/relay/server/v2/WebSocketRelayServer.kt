package io.opencola.relay.server.v2

import io.opencola.relay.common.State.*
import io.opencola.relay.common.connection.ConnectionDirectory
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import java.net.URI

// TODO: Remove?
class WebSocketRelayServer(
    connectionDirectory: ConnectionDirectory,
    messageStore: MemoryMessageStore? = null,
    address: URI,
    numChallengeBytes: Int = 32
) : Server(connectionDirectory, messageStore, address, numChallengeBytes) {
    override suspend fun open() {
        state = Open
        openMutex.unlock()
    }
}