package io.opencola.relay.common.message.store

import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.relay.common.message.v2.store.StoredMessage
import io.opencola.security.*
import org.junit.Test
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

class MessageStoreTest : SecurityProviderDependent() {
    private val emptyBytes = ByteArray(0)
    private val dummyMessageSecretKey = encrypt(generateKeyPair().public, emptyBytes)

    private fun String.toSignedBytes() : SignedBytes {
        return SignedBytes(
            Signature(SignatureAlgorithm.NONE, emptyBytes),
            this.toByteArray()
        )
    }

    private fun PublicKey.toRecipient() : Recipient {
        return Recipient(this, dummyMessageSecretKey)
    }

    private fun assertMatches(
        from: PublicKey,
        to: PublicKey,
        messageStorageKey: MessageStorageKey,
        message: SignedBytes,
        storedMessage: StoredMessage
    ) {
        assertEquals(storedMessage.from, from)
        assertEquals(storedMessage.to.publicKey, to)
        assertEquals(storedMessage.messageStorageKey, messageStorageKey)
        assertEquals(storedMessage.message, message)
    }

    @Test
    fun testBasicAdd() {
        val messageStore = MemoryMessageStore(32)
        val fromPublicKey = generateKeyPair().public
        val toPublicKey = generateKeyPair().public
        val messageStorageKey = MessageStorageKey.of("key")
        val message = "message".toSignedBytes()

        messageStore.addMessage(fromPublicKey, toPublicKey.toRecipient(), messageStorageKey, message)

        val storedMessage0 = messageStore.getMessages(toPublicKey).single()
        assertMatches(fromPublicKey, toPublicKey, messageStorageKey, message, storedMessage0)

        messageStore.removeMessage(storedMessage0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testAddMultipleMessages() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey = generateKeyPair().public
        val key0 = MessageStorageKey.of("key0")
        val message0 = "message0".toSignedBytes()
        messageStore.addMessage(fromPublicKey, toPublicKey.toRecipient(), key0, message0)

        val key1 = MessageStorageKey.of("key1")
        val message1 = "message1".toSignedBytes()
        messageStore.addMessage(fromPublicKey, toPublicKey.toRecipient(), key1, message1)

        val storedMessages = messageStore.getMessages(toPublicKey).toList()

        assert(storedMessages.size == 2)
        val storedMessage0 = storedMessages[0]
        assertMatches(fromPublicKey, toPublicKey, key0, message0, storedMessage0)

        val storedMessage1 = storedMessages[1]
        assertMatches(fromPublicKey, toPublicKey, key1, message1, storedMessage1)

        messageStore.removeMessage(storedMessage0)
        messageStore.removeMessage(storedMessage1)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testAddWithDuplicateMessageStorageKey() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey = generateKeyPair().public

        val key = MessageStorageKey.of("key")
        val message = "message".toSignedBytes()
        messageStore.addMessage(fromPublicKey, toPublicKey.toRecipient(), key, message)

        val message1 = "message1".toSignedBytes()
        messageStore.addMessage(fromPublicKey, toPublicKey.toRecipient(), key, message1)

        val storedMessage0 = messageStore.getMessages(toPublicKey).single()
        // Check that only 2nd message is returned (it should overwrite the first)
        assertMatches(fromPublicKey, toPublicKey, key, message1, storedMessage0)

        messageStore.removeMessage(storedMessage0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testRejectMessageWhenOverQuota() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey0 = generateKeyPair().public
        val fromPublicKey1 = generateKeyPair().public
        val key0 = MessageStorageKey.of("key")
        val message0 = "012345678901234567890123456789".toSignedBytes()
        messageStore.addMessage(fromPublicKey0, toPublicKey.toRecipient(), key0, message0)

        val key1 = MessageStorageKey.of("key1")
        val message1 = "0123456789".toSignedBytes()
        messageStore.addMessage(fromPublicKey1, toPublicKey.toRecipient(), key1, message1)

        // Check only first message is stored - 2nd message should be rejected
        val storedMessage0 = messageStore.getMessages(toPublicKey).single()
        assertMatches(fromPublicKey0, toPublicKey, key0, message0, storedMessage0)

        // Add same message from different sender - it should be rejected too
        messageStore.addMessage(fromPublicKey1, toPublicKey.toRecipient(), key0, message0)
        val storedMessage1 = messageStore.getMessages(toPublicKey).single()
        assertMatches(fromPublicKey0, toPublicKey, key0, message0, storedMessage1)

        // Remove message
        messageStore.removeMessage(storedMessage0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())

        // Now try adding the 2nd message again - it should be accepted this time
        messageStore.addMessage(fromPublicKey0, toPublicKey.toRecipient(), key1, message1)

        val storedMessage2 = messageStore.getMessages(toPublicKey).single()
        assertMatches(fromPublicKey0, toPublicKey, key1, message1, storedMessage2)
    }

    @Test
    fun testAddAddSameMessageDifferentFrom() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey0 = generateKeyPair().public
        val fromPublicKey1 = generateKeyPair().public
        val key = MessageStorageKey.of("key")
        val message = "message".toSignedBytes()

        assertNotEquals(fromPublicKey0, fromPublicKey1)

        // Even though messages are identical, they should be stored, as they come from different senders
        messageStore.addMessage(fromPublicKey0, toPublicKey.toRecipient(), key, message)
        messageStore.addMessage(fromPublicKey1, toPublicKey.toRecipient(), key, message)

        val storedMessages = messageStore.getMessages(toPublicKey).toList()
        assert(storedMessages.size == 2)

        val storedMessage0 = storedMessages[0]
        assertMatches(fromPublicKey0, toPublicKey, key, message, storedMessage0)

        val storedMessage1 = storedMessages[1]
        assertMatches(fromPublicKey1, toPublicKey, key, message, storedMessage1)
    }

    @Test
    fun testEnvelopeIdentity() {
        val messageStore = MemoryMessageStore(32)
        val fromPublicKey = generateKeyPair().public
        val toPublicKey = generateKeyPair().public
        val messageStorageKey = MessageStorageKey.of("key")
        val message = "message".toSignedBytes()

        messageStore.addMessage(fromPublicKey, toPublicKey.toRecipient(), messageStorageKey, message)

        assert(messageStore.getMessages(toPublicKey).single().message === message)
    }

    @Test
    fun testNoMessageStorageKey() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey = generateKeyPair().public
        val messageStorageKey = MessageStorageKey.none
        val message = "message".toSignedBytes()

        assertFails { messageStore.addMessage(fromPublicKey, toPublicKey.toRecipient(), messageStorageKey, message) }
    }
}