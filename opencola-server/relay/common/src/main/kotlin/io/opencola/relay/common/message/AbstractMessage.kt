package io.opencola.relay.common.message

import java.security.PublicKey
import java.util.UUID

abstract class AbstractMessage {
    abstract val id: UUID
    abstract val from: PublicKey
    abstract val body: ByteArray
}