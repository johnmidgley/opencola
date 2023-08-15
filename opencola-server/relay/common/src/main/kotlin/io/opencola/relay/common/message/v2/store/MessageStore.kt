package io.opencola.relay.common.message.v2.store

import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v2.MessageStorageKey
import java.security.PublicKey

interface MessageStore {
    fun addMessage(from: PublicKey, to: Recipient, messageStorageKey: MessageStorageKey, message: ByteArray)

    // Be careful not to modify the sequence as it is being iterated over. If you want to drain the queue,
    // iterate in a while loop, getting one message at a time and then remove it.
    fun getMessages(to: PublicKey): Sequence<StoredMessage>
    fun removeMessage(storedMessage: StoredMessage)
}