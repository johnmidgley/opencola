package io.opencola.relay.common.message.store

import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.security.generateKeyPair
import org.junit.Test
import kotlin.test.assertFails

class MessageStoreTest {

    @Test
    fun testBasicAdd() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val recipient = Recipient(toPublicKey)
        val fromPublicKey = generateKeyPair().public
        val messageStorageKey = MessageStorageKey.of("key")
        val message = "message".toByteArray()

        messageStore.addMessage(fromPublicKey, recipient, messageStorageKey, message)
        val storedMessages = messageStore.getMessages(toPublicKey).toList()

        assert(storedMessages.size == 1)
        val storedMessage0 = storedMessages[0]
        assert(storedMessage0.to.publicKey == toPublicKey)
        assert(storedMessage0.message.contentEquals(message))

        messageStore.removeMessage(storedMessage0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testAddMultipleMessages() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val recipient = Recipient(toPublicKey)
        val fromPublicKey = generateKeyPair().public
        val messageStorageKey = MessageStorageKey.of("key")
        val message = "message".toByteArray()

        messageStore.addMessage(fromPublicKey, recipient, messageStorageKey, message)

        val messageStorageKey1 = MessageStorageKey.of("key1")
        val message1 = "message1".toByteArray()

        messageStore.addMessage(fromPublicKey, recipient, messageStorageKey1, message1)

        val storedMessages = messageStore.getMessages(toPublicKey).toList()

        assert(storedMessages.size == 2)
        val storedMessage0 = storedMessages[0]
        assert(storedMessage0.to.publicKey == toPublicKey)
        assert(storedMessage0.message.contentEquals(message))

        val storedMessage1 = storedMessages[1]
        assert(storedMessage1.to.publicKey == toPublicKey)
        assert(storedMessage1.message.contentEquals(message1))

        messageStore.removeMessage(storedMessage0)
        messageStore.removeMessage(storedMessage1)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testAddWithDuplicateMessageStorageKey() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val recipient = Recipient(toPublicKey)

        val fromPublicKey = generateKeyPair().public
        val messageStorageKey = MessageStorageKey.of("key")
        val message = "message".toByteArray()

        messageStore.addMessage(fromPublicKey, recipient, messageStorageKey, message)

        val message1 = "message1".toByteArray()
        messageStore.addMessage(fromPublicKey, recipient, messageStorageKey, message1)

        val storedMessages = messageStore.getMessages(toPublicKey).toList()

        // Check that only 2nd message is returned (it should overwrite the first)
        assert(storedMessages.size == 1)
        val storedMessage0 = storedMessages[0]
        assert(storedMessage0.to.publicKey == toPublicKey)
        assert(storedMessage0.message.contentEquals(message1))

        messageStore.removeMessage(storedMessage0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testRejectMessageWhenOverQuota() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val recipient = Recipient(toPublicKey)
        val from1PublicKey = generateKeyPair().public
        val from2PublicKey = generateKeyPair().public
        val messageStorageKey = MessageStorageKey.of("key")
        val message = "012345678901234567890123456789".toByteArray()

        messageStore.addMessage(from1PublicKey, recipient, messageStorageKey, message)

        val messageStorageKey1 = MessageStorageKey.of("key1")
        val message1 = "0123456789".toByteArray()

        messageStore.addMessage(from1PublicKey, recipient, messageStorageKey1, message1)

        // Check only first message is stored - 2nd message should be rejected
        val storedMessages = messageStore.getMessages(toPublicKey).toList()
        assert(storedMessages.size == 1)
        val storedMessage0 = storedMessages[0]
        assert(storedMessage0.to.publicKey == toPublicKey)
        assert(storedMessage0.message.contentEquals(message))

        // Add same message from different sender - it should be rejected too
        messageStore.addMessage(from2PublicKey, recipient, messageStorageKey, message)
        val storedMessages1 = messageStore.getMessages(toPublicKey).toList()
        assert(storedMessages1.size == 1)
        val storedMessage1 = storedMessages[0]
        assert(storedMessage1.to.publicKey == toPublicKey)
        assert(storedMessage1.message.contentEquals(message))

        // Remove message
        messageStore.removeMessage(storedMessage0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())

        // Now try adding the 2nd message again - it should be accepted this time
        messageStore.addMessage(from1PublicKey, recipient, messageStorageKey1, message1)

        val storedMessages2 = messageStore.getMessages(toPublicKey).toList()
        assert(storedMessages2.size == 1)
        val storedMessage2 = storedMessages2[0]
        assert(storedMessage2.to.publicKey == toPublicKey)
        assert(storedMessage2.message.contentEquals(message1))
    }

    @Test
    fun testAddAddSameMessageDifferentFrom() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val recipient = Recipient(toPublicKey)
        val from1PublicKey = generateKeyPair().public
        val from2PublicKey = generateKeyPair().public
        val messageStorageKey = MessageStorageKey.of("key")
        val message = "0123456789".toByteArray()

        assert(!from1PublicKey.equals(from2PublicKey))

        // Even though messages are identical, they should be stored, as they come from different senders
        messageStore.addMessage(from1PublicKey, recipient, messageStorageKey, message)
        messageStore.addMessage(from2PublicKey, recipient, messageStorageKey, message)

        val storedMessages = messageStore.getMessages(toPublicKey).toList()
        assert(storedMessages.size == 2)

        val storedMessage0 = storedMessages[0]
        assert(storedMessage0.to.publicKey == toPublicKey)
        assert(storedMessage0.message.contentEquals(message))

        val storedMessage1 = storedMessages[1]
        assert(storedMessage1.to.publicKey == toPublicKey)
        assert(storedMessage1.message.contentEquals(message))

        assert(storedMessage0.senderSpecificKey != storedMessage1.senderSpecificKey)
    }

    @Test
    fun testNoMessageStorageKey() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val recipient = Recipient(toPublicKey)
        val fromPublicKey = generateKeyPair().public
        val messageStorageKey = MessageStorageKey.none
        val message = "message".toByteArray()

        assertFails { messageStore.addMessage(fromPublicKey, recipient, messageStorageKey, message) }
    }
}