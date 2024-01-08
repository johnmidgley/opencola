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
    private val defaultMaxBytesStored = 1024 * 1024 * 50L
    private val defaultMaxBytesStoredPerUser = 32L

    init {
        initProvider()
    }

    private fun getMemoryMessageStore(maxStoredBytes: Long, maxStoredBytesPerUser: Long): MemoryMessageStore {
        val defaultPolicy = Policy("default", storagePolicy = StoragePolicy(maxStoredBytesPerUser))
        val policyStore = MemoryPolicyStore(Id.new(), defaultPolicy)
        return MemoryMessageStore(maxStoredBytes, policyStore)
    }

    @Test
    fun testBasicAdd() {
        val messageStore = getMemoryMessageStore(defaultMaxBytesStored, defaultMaxBytesStoredPerUser)
        testBasicAdd(messageStore)
    }

    @Test
    fun testAddMultipleMessages() {
        val messageStore = getMemoryMessageStore(defaultMaxBytesStored, defaultMaxBytesStoredPerUser)
        testAddMultipleMessages(messageStore)
    }

    @Test
    fun testAddWithDuplicateMessageStorageKey() {
        val messageStore = getMemoryMessageStore(defaultMaxBytesStored, defaultMaxBytesStoredPerUser)
        testAddWithDuplicateMessageStorageKey(messageStore)
    }

    @Test
    fun testRejectMessageWhenOverQuota() {
        val messageStore = getMemoryMessageStore(defaultMaxBytesStored, defaultMaxBytesStoredPerUser)
        testRejectMessageWhenOverQuota(messageStore)
    }

    @Test
    fun testAddAddSameMessageDifferentFrom() {
        val messageStore = getMemoryMessageStore(defaultMaxBytesStored, defaultMaxBytesStoredPerUser)
        testAddAddSameMessageDifferentFrom(messageStore)
    }

    @Test
    fun testEnvelopeIdentity() {
        val messageStore = getMemoryMessageStore(defaultMaxBytesStored, defaultMaxBytesStoredPerUser)
        val from = Id.new()
        val to = Id.new()
        val messageStorageKey = MessageStorageKey.of("key")
        val message = "message".toSignedBytes()

        messageStore.addMessage(from, to, messageStorageKey, dummyMessageSecretKey, message)

        assert(messageStore.getMessages(to).single().body === message)
    }

    @Test
    fun testNoMessageStorageKey() {
        val messageStore = getMemoryMessageStore(defaultMaxBytesStored, defaultMaxBytesStoredPerUser)
        testNoMessageStorageKey(messageStore)
    }

    @Test
    fun testConsumeMessages() {
        val messageStore = getMemoryMessageStore(defaultMaxBytesStored, 1024 * 1024)
        testConsumeMessages(messageStore)
    }

    @Test
    fun testRemoveMessagesByAge() {
        val messageStore = getMemoryMessageStore(defaultMaxBytesStored, 1024 * 1024)
        testRemoveMessagesByAge(messageStore)
    }
}