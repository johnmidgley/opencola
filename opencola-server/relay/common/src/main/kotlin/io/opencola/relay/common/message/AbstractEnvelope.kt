package io.opencola.relay.common.message

import io.opencola.relay.common.message.v2.MessageStorageKey

abstract class AbstractEnvelope(
    val recipients: List<Recipient>,
    val messageStorageKey: MessageStorageKey,
    val message: ByteArray
)