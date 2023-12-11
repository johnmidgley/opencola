package io.opencola.relay.server.v1

import io.opencola.event.log.EventLogger
import io.opencola.relay.common.State.*
import io.opencola.relay.server.RelayConfig
import java.net.URI


// TODO: Remove?
class WebSocketRelayServer(
    config: RelayConfig,
    eventLogger: EventLogger,
    address: URI,
) : Server(config, eventLogger, address) {
    override suspend fun open() {
        state = Open
        openMutex.unlock()
    }
}