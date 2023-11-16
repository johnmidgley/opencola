package io.opencola.relay.server.v2

import io.opencola.event.log.EventLogger
import io.opencola.relay.common.State.*
import io.opencola.relay.common.connection.ConnectionDirectory
import io.opencola.relay.common.message.v2.store.MessageStore
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.relay.server.Config

// TODO: Remove?
class WebSocketRelayServer(
    config: Config,
    eventLogger: EventLogger,
    policyStore: PolicyStore,
    connectionDirectory: ConnectionDirectory,
    messageStore: MessageStore? = null,
) : Server(config, eventLogger, policyStore, connectionDirectory, messageStore) {
    override suspend fun open() {
        state = Open
        openMutex.unlock()
    }
}