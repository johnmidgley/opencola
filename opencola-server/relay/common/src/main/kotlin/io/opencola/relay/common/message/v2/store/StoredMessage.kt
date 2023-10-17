package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.SignedBytes
import java.security.PublicKey
import java.util.UUID

data class StoredMessage (
    val id: UUID,
    val from: PublicKey,
    val to: Recipient,
    val messageStorageKey: MessageStorageKey,
    val message: SignedBytes
) {
    constructor(from: PublicKey, to: Recipient, messageStorageKey: MessageStorageKey, message: SignedBytes) : this(
        UUID.randomUUID(),
        from,
        to,
        messageStorageKey,
        message)

    override fun toString(): String {
        return "StoredMessage(id=$id, from=${Id.ofPublicKey(from)}, to=$to, messageStorageKey=$messageStorageKey, message=${message.bytes.size} bytes)"
    }
}