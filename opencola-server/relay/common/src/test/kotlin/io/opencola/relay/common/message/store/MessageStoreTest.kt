package io.opencola.relay.common.message.store

import io.opencola.relay.common.message.v1.Envelope
import io.opencola.relay.common.message.v2.MessageKey
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.security.generateKeyPair
import org.junit.Test
import kotlin.test.assertFails

class MessageStoreTest {

    @Test
    fun testBasicAdd() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey = generateKeyPair().public

        val envelope = Envelope(
            to = toPublicKey,
            key = MessageKey.of("key"),
            message = "message".toByteArray()
        )

        messageStore.addMessage(fromPublicKey, envelope)
        val storedMessages = messageStore.getMessages(toPublicKey).toList()

        assert(storedMessages.size == 1)
        val message0 = storedMessages[0]
        assert(message0.envelope == envelope)
        assert(message0.envelope.to == toPublicKey)
        assert(message0.envelope.key == envelope.key)
        assert(message0.envelope.message.contentEquals(envelope.message))

        messageStore.removeMessage(message0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testAddMultipleMessages() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey = generateKeyPair().public

        val envelope = Envelope(
            to = toPublicKey,
            key = MessageKey.of("key"),
            message = "message".toByteArray()
        )
        messageStore.addMessage(fromPublicKey, envelope)

        val envelope1 = Envelope(
            to = toPublicKey,
            key = MessageKey.of("key1"),
            message = "message1".toByteArray()
        )
        messageStore.addMessage(fromPublicKey, envelope1)

        val storedMessages = messageStore.getMessages(toPublicKey).toList()

        // Check that only 2nd message is returned (it should overwrite the first)
        assert(storedMessages.size == 2)
        val message0 = storedMessages[0]
        assert(message0.envelope == envelope)
        assert(message0.envelope.to == toPublicKey)
        assert(message0.envelope.key == envelope.key)
        assert(message0.envelope.message.contentEquals(envelope.message))

        val message1 = storedMessages[1]
        assert(message1.envelope == envelope1)
        assert(message1.envelope.to == toPublicKey)
        assert(message1.envelope.key == envelope1.key)
        assert(message1.envelope.message.contentEquals(envelope1.message))

        messageStore.removeMessage(message0)
        messageStore.removeMessage(message1)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testAddWithDuplicateMessageKey() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey = generateKeyPair().public

        val envelope = Envelope(
            to = toPublicKey,
            key = MessageKey.of("key"),
            message = "message".toByteArray()
        )

        messageStore.addMessage(fromPublicKey, envelope)

        val envelope1 = Envelope(
            to = toPublicKey,
            key = MessageKey.of("key"),
            message = "message1".toByteArray()
        )

        messageStore.addMessage(fromPublicKey, envelope1)

        val storedMessages = messageStore.getMessages(toPublicKey).toList()

        // Check that only 2nd message is returned (it should overwrite the first)
        assert(storedMessages.size == 1)
        val message0 = storedMessages[0]
        assert(message0.envelope == envelope1)
        assert(message0.envelope.to == toPublicKey)
        assert(message0.envelope.key == envelope1.key)
        assert(message0.envelope.message.contentEquals(envelope1.message))

        messageStore.removeMessage(message0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testRejectMessageWhenOverQuota() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val from1PublicKey = generateKeyPair().public
        val from2PublicKey = generateKeyPair().public

        val envelope = Envelope(
            to = toPublicKey,
            key = MessageKey.of("key"),
            message = "012345678901234567890123456789".toByteArray()
        )

        messageStore.addMessage(from1PublicKey, envelope)

        val envelope1 = Envelope(
            to = toPublicKey,
            key = MessageKey.of("key1"),
            message = "0123456789".toByteArray()
        )

        messageStore.addMessage(from1PublicKey, envelope1)


        // Check only first message is stored - 2nd message should be rejected
        val storedMessages = messageStore.getMessages(toPublicKey).toList()
        assert(storedMessages.size == 1)
        val message0 = storedMessages[0]
        assert(message0.envelope == envelope)
        assert(message0.envelope.to == toPublicKey)
        assert(message0.envelope.key == envelope.key)
        assert(message0.envelope.message.contentEquals(envelope.message))

        // Add same message from different sender - it should be rejected too
        messageStore.addMessage(from2PublicKey, envelope)
        val storedMessages1 = messageStore.getMessages(toPublicKey).toList()
        assert(storedMessages1.size == 1)
        val message1 = storedMessages[0]
        assert(message1.envelope == envelope)
        assert(message1.envelope.to == toPublicKey)
        assert(message1.envelope.key == envelope.key)
        assert(message1.envelope.message.contentEquals(envelope.message))

        // Remove message
        messageStore.removeMessage(message0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())

        // Now try adding the 2nd message again - it should be accepted this time
        messageStore.addMessage(from1PublicKey, envelope1)

        val storedMessages2 = messageStore.getMessages(toPublicKey).toList()
        assert(storedMessages2.size == 1)
        val message2 = storedMessages2[0]
        assert(message2.envelope == envelope1)
        assert(message2.envelope.to == toPublicKey)
        assert(message2.envelope.key == envelope1.key)
        assert(message2.envelope.message.contentEquals(envelope1.message))
    }

    @Test
    fun testAddAddSameMessageDifferentFrom() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val from1PublicKey = generateKeyPair().public
        val from2PublicKey = generateKeyPair().public

        assert(!from1PublicKey.equals(from2PublicKey))

        val envelope = Envelope(
            to = toPublicKey,
            key = MessageKey.of("key"),
            message = "0123456789".toByteArray()
        )

        // Even though messages are identical, they should be stored, as they come from different senders
        messageStore.addMessage(from1PublicKey, envelope)
        messageStore.addMessage(from2PublicKey, envelope)

        val storedMessages = messageStore.getMessages(toPublicKey).toList()
        assert(storedMessages.size == 2)

        val message0 = storedMessages[0]
        assert(message0.envelope == envelope)
        assert(message0.envelope.to == toPublicKey)
        assert(message0.envelope.key == envelope.key)
        assert(message0.envelope.message.contentEquals(envelope.message))

        val message1 = storedMessages[1]
        assert(message1.envelope == envelope)
        assert(message1.envelope.to == toPublicKey)
        assert(message1.envelope.key == envelope.key)
        assert(message1.envelope.message.contentEquals(envelope.message))

        assert(message0.senderSpecificKey != message1.senderSpecificKey)
    }

    @Test
    fun testNoMessageKey() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey = generateKeyPair().public

        val envelope = Envelope(
            to = toPublicKey,
            key = MessageKey.none,
            message = "message".toByteArray()
        )

        assertFails { messageStore.addMessage(fromPublicKey, envelope) }
    }
}