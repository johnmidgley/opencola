package io.opencola.relay.cli

import io.opencola.relay.client.v2.Client
import io.opencola.relay.common.message.v2.AdminMessage
import kotlinx.coroutines.channels.Channel
import java.nio.file.Path
import java.security.KeyPair

class Context(
    val storagePath: Path,
    val keyPair: KeyPair,
    val client: Client,
    val responseChannel: Channel<AdminMessage>
)