package io.opencola.relay.common.message.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.security.initProvider
import kotlin.test.Test

class MemoryMessageStoreTest {
    init {
        initProvider()
    }

    @Test
    fun testBasicAdd() {
        val messageStore = MemoryMessageStore(32)
        testBasicAdd(messageStore)
    }

    @Test
    fun testAddMultipleMessages() {
        val messageStore = MemoryMessageStore(32)
        testAddMultipleMessages(messageStore)
    }

    @Test
    fun testAddWithDuplicateMessageStorageKey() {
        val messageStore = MemoryMessageStore(32)
        testAddWithDuplicateMessageStorageKey(messageStore)
    }

    @Test
    fun testRejectMessageWhenOverQuota() {
        val messageStore = MemoryMessageStore(32)
        testRejectMessageWhenOverQuota(messageStore)
    }

    @Test
    fun testAddAddSameMessageDifferentFrom() {
        val messageStore = MemoryMessageStore(32)
        testAddAddSameMessageDifferentFrom(messageStore)
    }

    @Test
    fun testEnvelopeIdentity() {
        val messageStore = MemoryMessageStore(32)
        val from = Id.new()
        val to = Id.new()
        val messageStorageKey = MessageStorageKey.of("key")
        val message = "message".toSignedBytes()

        messageStore.addMessage(from, to, messageStorageKey, dummyMessageSecretKey, message)

        assert(messageStore.getMessages(to).single().message === message)
    }

    @Test
    fun testNoMessageStorageKey() {
        val messageStore = MemoryMessageStore(32)
        testNoMessageStorageKey(messageStore)
    }

    @Test
    fun testConsumeMessages() {
        val messageStore = MemoryMessageStore()
        testConsumeMessages(messageStore)
    }
}