package io.opencola.relay.common.message.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.message.v2.store.MessageStore
import io.opencola.relay.common.message.v2.store.StoredMessage
import io.opencola.security.*
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

private val emptyBytes = ByteArray(0)
val dummyMessageSecretKey = encrypt(generateKeyPair().public, emptyBytes)

fun String.toSignedBytes(): SignedBytes {
    return SignedBytes(
        Signature(SignatureAlgorithm.NONE, emptyBytes),
        this.toByteArray()
    )
}

fun assertMatches(
    from: Id,
    to: Id,
    messageStorageKey: MessageStorageKey,
    messageSecretKey: EncryptedBytes,
    message: SignedBytes,
    storedMessage: StoredMessage
) {
    assertEquals(storedMessage.header.from, from)
    assertEquals(storedMessage.header.to, to)
    assertEquals(storedMessage.header.storageKey, messageStorageKey)
    assertEquals(storedMessage.header.secretKey, messageSecretKey)
    assertEquals(storedMessage.body, message)
}

fun testBasicAdd(messageStore: MessageStore) {
    val from = Id.new()
    val to = Id.new()
    val messageStorageKey = MessageStorageKey.of("key")
    val message = "message".toSignedBytes()

    messageStore.addMessage(from, to, messageStorageKey, dummyMessageSecretKey, message)

    val storedMessage0 = messageStore.getMessages(to).single()
    assertMatches(from, to, messageStorageKey, dummyMessageSecretKey, message, storedMessage0)

    messageStore.removeMessage(storedMessage0.header)
    assert(messageStore.getMessages(to).toList().isEmpty())
}

fun testAddMultipleMessages(messageStore: MessageStore) {
    val from = Id.new()
    val to = Id.new()
    val key0 = MessageStorageKey.of("key0")
    val message0 = "message0".toSignedBytes()
    messageStore.addMessage(from, to, key0, dummyMessageSecretKey, message0)

    val key1 = MessageStorageKey.of("key1")
    val message1 = "message1".toSignedBytes()
    messageStore.addMessage(from, to, key1, dummyMessageSecretKey, message1)

    val storedMessages = messageStore.getMessages(to).toList()

    assert(storedMessages.size == 2)
    val storedMessage0 = storedMessages[0]
    assertMatches(from, to, key0, dummyMessageSecretKey, message0, storedMessage0)

    val storedMessage1 = storedMessages[1]
    assertMatches(from, to, key1, dummyMessageSecretKey, message1, storedMessage1)

    messageStore.removeMessage(storedMessage0.header)
    messageStore.removeMessage(storedMessage1.header)
    assert(messageStore.getMessages(to).toList().isEmpty())
}

fun testAddWithDuplicateMessageStorageKey(messageStore: MessageStore) {
    val from = Id.new()
    val to = Id.new()

    val key = MessageStorageKey.of("key")
    val message = "message".toSignedBytes()
    messageStore.addMessage(from, to, key, dummyMessageSecretKey, message)

    val message1 = "message1".toSignedBytes()
    messageStore.addMessage(from, to, key, dummyMessageSecretKey, message1)

    val storedMessage0 = messageStore.getMessages(to).single()
    // Check that only 2nd message is returned (it should overwrite the first)
    assertMatches(from, to, key, dummyMessageSecretKey, message1, storedMessage0)

    messageStore.removeMessage(storedMessage0.header)
    assert(messageStore.getMessages(to).toList().isEmpty())
}

fun testRejectMessageWhenOverQuota(messageStore: MessageStore) {
    val from0 = Id.new()
    val from1 = Id.new()
    val to = Id.new()
    val key0 = MessageStorageKey.of("key")
    val message0 = "012345678901234567890123456789".toSignedBytes()
    messageStore.addMessage(from0, to, key0, dummyMessageSecretKey, message0)

    val key1 = MessageStorageKey.of("key1")
    val message1 = "0123456789".toSignedBytes()
    messageStore.addMessage(from1, to, key1, dummyMessageSecretKey, message1)

    // Check only first message is stored - 2nd message should be rejected
    val storedMessage0 = messageStore.getMessages(to).single()
    assertMatches(from0, to, key0, dummyMessageSecretKey, message0, storedMessage0)

    // Add same message from different sender - it should be rejected too
    messageStore.addMessage(from1, to, key0, dummyMessageSecretKey, message0)
    val storedMessage1 = messageStore.getMessages(to).single()
    assertMatches(from0, to, key0, dummyMessageSecretKey, message0, storedMessage1)

    // Remove message
    messageStore.removeMessage(storedMessage0.header)
    assert(messageStore.getMessages(to).toList().isEmpty())

    // Now try adding the 2nd message again - it should be accepted this time
    messageStore.addMessage(from0, to, key1, dummyMessageSecretKey, message1)

    val storedMessage2 = messageStore.getMessages(to).single()
    assertMatches(from0, to, key1, dummyMessageSecretKey, message1, storedMessage2)
}

fun testAddAddSameMessageDifferentFrom(messageStore: MessageStore) {
    val from0 = Id.new()
    val from1 = Id.new()
    val to = Id.new()
    val key = MessageStorageKey.of("key")
    val message = "message".toSignedBytes()

    assertNotEquals(from0, from1)

    // Even though messages are identical, they should be stored, as they come from different senders
    messageStore.addMessage(from0, to, key, dummyMessageSecretKey, message)
    messageStore.addMessage(from1, to, key, dummyMessageSecretKey, message)

    val storedMessages = messageStore.getMessages(to).toList()
    assert(storedMessages.size == 2)

    val storedMessage0 = storedMessages[0]
    assertMatches(from0, to, key, dummyMessageSecretKey, message, storedMessage0)

    val storedMessage1 = storedMessages[1]
    assertMatches(from1, to, key, dummyMessageSecretKey, message, storedMessage1)
}

fun testNoMessageStorageKey(messageStore: MessageStore) {
    val from = Id.new()
    val to = Id.new()
    val messageStorageKey = MessageStorageKey.none
    val message = "message".toSignedBytes()

    assertFails { messageStore.addMessage(from, to, messageStorageKey, dummyMessageSecretKey, message) }
}

fun testConsumeMessages(messageStore: MessageStore) {
    val to = Id.new()

    // Check that peeking at a message doesn't cause it to be removed
    messageStore.addMessage(Id.new(), to, MessageStorageKey.unique(), dummyMessageSecretKey, "message".toSignedBytes())
    val peekedMessage = messageStore.getMessages(to).first()
    println("peekedMessage: $peekedMessage")
    assertEquals(1, messageStore.getMessages(to).toList().size)

    // Make sure that accessing the end of the list removes it
    val consumedMessage = messageStore.consumeMessages(to).single()
    println("consumedMessage: $consumedMessage")
    assertEquals(0, messageStore.getMessages(to).toList().size)

    // Test a bunch of messages that requires multiple batches to be consumed
    val messages = (0..101).map {
        StoredMessage(Id.new(), to, MessageStorageKey.unique(), dummyMessageSecretKey, "message$it".toSignedBytes())
    }

    messages.forEach {
        messageStore.addMessage(it.header.from, it.header.to, it.header.storageKey, it.header.secretKey, it.body)
    }

    // Consume all messages using (which is forced by .toList())
    val consumeMessages = messageStore.consumeMessages(to).toList()

    assert(messageStore.getMessages(to).toList().isEmpty())
    assertEquals(messages.size, consumeMessages.size)

    consumeMessages.zip(messages).forEach { (consumed, original) ->
        assertMatches(original.header.from, original.header.to, original.header.storageKey, original.header.secretKey, original.body, consumed)
    }
}

fun testRemoveMessagesByAge(messageStore: MessageStore) {
    val to = Id.new()
    val message = "message".toSignedBytes()

    println("Adding message and delaying")
    messageStore.addMessage(Id.new(), to, MessageStorageKey.unique(), dummyMessageSecretKey, message)
    Thread.sleep(100)

    println("Added 2nd message")
    val keptStorageKey = MessageStorageKey.of("kept")
    messageStore.addMessage(Id.new(), to, keptStorageKey, dummyMessageSecretKey, message)

    println("Checking both messages are in the store")
    assertEquals(2, messageStore.getMessages(to).toList().size)

    println("Removing messages older than 50ms")
    messageStore.removeMessages(50).forEach { println("Removed message: $it") }

    println("Checking only 1 message is left")
    val messages = messageStore.getMessages(to).toList()
    assertEquals(1, messages.size)
    assertEquals(keptStorageKey, messages[0].header.storageKey)
}
