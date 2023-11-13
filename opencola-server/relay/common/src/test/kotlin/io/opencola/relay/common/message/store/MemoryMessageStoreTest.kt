package io.opencola.relay.common.message.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.relay.common.policy.MemoryPolicyStore
import io.opencola.relay.common.policy.Policy
import io.opencola.relay.common.policy.StoragePolicy
import io.opencola.security.initProvider
import kotlin.test.Test

class MemoryMessageStoreTest {
    init {
        initProvider()
    }

    private fun getMemoryMessageStore(maxStoredBytesPerUser: Int) : MemoryMessageStore {
        val defaultPolicy = Policy("default", storagePolicy = StoragePolicy(maxStoredBytesPerUser))
        val policyStore = MemoryPolicyStore(Id.new(), defaultPolicy)
        return MemoryMessageStore(policyStore)
    }

    @Test
    fun testBasicAdd() {
        val messageStore = getMemoryMessageStore(32)
        testBasicAdd(messageStore)
    }

    @Test
    fun testAddMultipleMessages() {
        val messageStore = getMemoryMessageStore(32)
        testAddMultipleMessages(messageStore)
    }

    @Test
    fun testAddWithDuplicateMessageStorageKey() {
        val messageStore = getMemoryMessageStore(32)
        testAddWithDuplicateMessageStorageKey(messageStore)
    }

    @Test
    fun testRejectMessageWhenOverQuota() {
        val messageStore = getMemoryMessageStore(32)
        testRejectMessageWhenOverQuota(messageStore)
    }

    @Test
    fun testAddAddSameMessageDifferentFrom() {
        val messageStore = getMemoryMessageStore(32)
        testAddAddSameMessageDifferentFrom(messageStore)
    }

    @Test
    fun testEnvelopeIdentity() {
        val messageStore = getMemoryMessageStore(32)
        val from = Id.new()
        val to = Id.new()
        val messageStorageKey = MessageStorageKey.of("key")
        val message = "message".toSignedBytes()

        messageStore.addMessage(from, to, messageStorageKey, dummyMessageSecretKey, message)

        assert(messageStore.getMessages(to).single().body === message)
    }

    @Test
    fun testNoMessageStorageKey() {
        val messageStore = getMemoryMessageStore(32)
        testNoMessageStorageKey(messageStore)
    }

    @Test
    fun testConsumeMessages() {
        val messageStore = getMemoryMessageStore(1024 * 1024)
        testConsumeMessages(messageStore)
    }

    @Test
    fun testRemoveMessagesByAge() {
        val messageStore = getMemoryMessageStore(1024 * 1024)
        testRemoveMessagesByAge(messageStore)
    }
}