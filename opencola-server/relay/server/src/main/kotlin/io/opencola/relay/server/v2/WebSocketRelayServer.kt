/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.relay.server.v2

import io.opencola.event.log.EventLogger
import io.opencola.relay.common.State.*
import io.opencola.relay.common.connection.ConnectionDirectory
import io.opencola.relay.common.message.v2.store.MessageStore
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.relay.server.RelayConfig

// TODO: Remove?
class WebSocketRelayServer(
    config: RelayConfig,
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