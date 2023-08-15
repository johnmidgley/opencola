package io.opencola.relay.common.message

import io.opencola.relay.common.message.v2.MessageStorageKey

abstract class AbstractEnvelope {
    abstract val recipients: List<Recipient>
    abstract val messageStorageKey: MessageStorageKey
    abstract val message: AbstractMessage
}