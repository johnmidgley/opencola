package io.opencola.network.message

import io.opencola.relay.common.message.v2.MessageStorageKey
import java.util.*

abstract class Message(val messageStorageKey: MessageStorageKey, val id: UUID = UUID.randomUUID()) {
    override fun toString(): String {
        return "${this::class.simpleName}(id=$id)"
    }
}