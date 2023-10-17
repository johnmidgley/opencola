package io.opencola.relay.common.message.v2.store

import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.SignedBytes
import java.security.PublicKey

interface MessageStore {
    // The message is stored, vs. the encodedMessage, for efficiency reasons. Storing things this way
    // ensures that the message is not replicated in the store across recipients
    fun addMessage(from: PublicKey, to: Recipient, messageStorageKey: MessageStorageKey, message: SignedBytes)

    // Be careful not to modify the sequence as it is being iterated over. If you want to drain the queue,
    // iterate in a while loop, getting one message at a time and then remove it.
    fun getMessages(to: PublicKey): Sequence<StoredMessage>
    fun removeMessage(storedMessage: StoredMessage)
}