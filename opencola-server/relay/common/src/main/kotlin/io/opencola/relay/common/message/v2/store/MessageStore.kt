package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes

interface MessageStore {
    // The message is stored, vs. the encodedMessage, for efficiency reasons. Storing things this way
    // ensures that the message is not replicated in the store across recipients
    fun addMessage(
        from: Id,
        to: Id,
        messageStorageKey: MessageStorageKey,
        messageSecretKey: EncryptedBytes,
        message: SignedBytes
    )

    // Be careful not to modify the sequence as it is being iterated over. If you want to drain the queue,
    // iterate in a while loop, getting one message at a time and then remove it.
    fun getMessages(to: Id): Sequence<StoredMessage>
    fun removeMessage(storedMessage: StoredMessage)
}