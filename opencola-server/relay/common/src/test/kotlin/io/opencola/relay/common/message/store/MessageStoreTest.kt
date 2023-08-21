package io.opencola.relay.common.message.store

import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.security.*
import org.junit.Test
import java.security.PublicKey
import kotlin.test.assertFails

class MessageStoreTest : SecurityProviderDependent() {
    private val dummyMessageSecretKey = encrypt(generateKeyPair().public, ByteArray(0))

    private fun String.toEncryptedBytes() : EncryptedBytes {
        return EncryptedBytes(
            EncryptionTransformation.NONE,
            EncryptionParameters(EncryptionParameters.Type.NONE, ByteArray(0)),
            this.toByteArray()
        )
    }

    private fun getEnvelope(to: PublicKey, key: String, message: String) : Envelope {
        return Envelope(Recipient(to, dummyMessageSecretKey), MessageStorageKey.of(key), message.toEncryptedBytes())
    }

    @Test
    fun testBasicAdd() {
        val messageStore = MemoryMessageStore(32)
        val fromPublicKey = generateKeyPair().public
        val toPublicKey = generateKeyPair().public
        val envelope = getEnvelope(toPublicKey, "key", "message")

        messageStore.addMessage(fromPublicKey, toPublicKey, envelope)

        val storedMessage0 = messageStore.getMessages(toPublicKey).single()
        assert(storedMessage0.to == toPublicKey)
        assert(storedMessage0.envelope == envelope)

        messageStore.removeMessage(storedMessage0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testAddMultipleMessages() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey = generateKeyPair().public
        val envelope = getEnvelope(toPublicKey, "key", "message")
        messageStore.addMessage(fromPublicKey, toPublicKey, envelope)

        val envelope1 = getEnvelope(toPublicKey, "key1", "message1")
        messageStore.addMessage(fromPublicKey, toPublicKey, envelope1)

        val storedMessages = messageStore.getMessages(toPublicKey).toList()

        assert(storedMessages.size == 2)
        val storedMessage0 = storedMessages[0]
        assert(storedMessage0.to == toPublicKey)
        assert(storedMessage0.envelope == envelope)

        val storedMessage1 = storedMessages[1]
        assert(storedMessage1.to == toPublicKey)
        assert(storedMessage1.envelope == envelope1)

        messageStore.removeMessage(storedMessage0)
        messageStore.removeMessage(storedMessage1)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testAddWithDuplicateMessageStorageKey() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey = generateKeyPair().public

        val envelope = getEnvelope(toPublicKey, "key", "message")
        messageStore.addMessage(fromPublicKey, toPublicKey, envelope)

        val envelope1 = getEnvelope(toPublicKey, "key", "message1")
        messageStore.addMessage(fromPublicKey, toPublicKey, envelope1)

        val storedMessage0 = messageStore.getMessages(toPublicKey).single()
        // Check that only 2nd message is returned (it should overwrite the first)
        assert(storedMessage0.to == toPublicKey)
        assert(storedMessage0.envelope == envelope1)

        messageStore.removeMessage(storedMessage0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())
    }

    @Test
    fun testRejectMessageWhenOverQuota() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val from1PublicKey = generateKeyPair().public
        val from2PublicKey = generateKeyPair().public
        val envelope = getEnvelope(toPublicKey, "key", "012345678901234567890123456789")

        messageStore.addMessage(from1PublicKey, toPublicKey, envelope)

        val envelope1 = getEnvelope(toPublicKey, "key1", "0123456789")
        messageStore.addMessage(from1PublicKey, toPublicKey, envelope1)

        // Check only first message is stored - 2nd message should be rejected
        val storedMessage0 = messageStore.getMessages(toPublicKey).single()
        assert(storedMessage0.to == toPublicKey)
        assert(storedMessage0.envelope == envelope)

        // Add same message from different sender - it should be rejected too
        messageStore.addMessage(from2PublicKey, toPublicKey, envelope)
        val storedMessage1 = messageStore.getMessages(toPublicKey).single()
        assert(storedMessage1.to == toPublicKey)
        assert(storedMessage1.envelope == envelope)

        // Remove message
        messageStore.removeMessage(storedMessage0)
        assert(messageStore.getMessages(toPublicKey).toList().isEmpty())

        // Now try adding the 2nd message again - it should be accepted this time
        messageStore.addMessage(from1PublicKey, toPublicKey, envelope1)

        val storedMessage2 = messageStore.getMessages(toPublicKey).single()
        assert(storedMessage2.to == toPublicKey)
        assert(storedMessage2.envelope == envelope1)
    }

    @Test
    fun testAddAddSameMessageDifferentFrom() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val from1PublicKey = generateKeyPair().public
        val from2PublicKey = generateKeyPair().public

        assert(!from1PublicKey.equals(from2PublicKey))

        val envelope = getEnvelope(toPublicKey, "key", "message")

        // Even though messages are identical, they should be stored, as they come from different senders
        messageStore.addMessage(from1PublicKey, toPublicKey, envelope)
        messageStore.addMessage(from2PublicKey, toPublicKey, envelope)

        val storedMessages = messageStore.getMessages(toPublicKey).toList()
        assert(storedMessages.size == 2)

        val storedMessage0 = storedMessages[0]
        assert(storedMessage0.to == toPublicKey)
        assert(storedMessage0.envelope == envelope)

        val storedMessage1 = storedMessages[1]
        assert(storedMessage1.to == toPublicKey)
        assert(storedMessage1.envelope == envelope)

        assert(storedMessage0.senderSpecificKey != storedMessage1.senderSpecificKey)
    }

    @Test
    fun testEnvelopeIdentity() {
        val messageStore = MemoryMessageStore(32)
        val fromPublicKey = generateKeyPair().public
        val toPublicKey = generateKeyPair().public
        val envelope = getEnvelope(toPublicKey, "key", "message")

        messageStore.addMessage(fromPublicKey, toPublicKey, envelope)

        assert(messageStore.getMessages(toPublicKey).single().envelope === envelope)
    }

    @Test
    fun testNoMessageStorageKey() {
        val messageStore = MemoryMessageStore(32)
        val toPublicKey = generateKeyPair().public
        val fromPublicKey = generateKeyPair().public
        val messageStorageKey = MessageStorageKey.none
        val message = "message".toEncryptedBytes()
        val envelope = Envelope(Recipient(toPublicKey, dummyMessageSecretKey), messageStorageKey, message)

        assertFails { messageStore.addMessage(fromPublicKey, toPublicKey, envelope) }
    }
}