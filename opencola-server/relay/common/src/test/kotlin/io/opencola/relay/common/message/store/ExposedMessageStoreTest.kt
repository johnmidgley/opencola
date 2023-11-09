package io.opencola.relay.common.message.store

import io.opencola.application.TestApplication
import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.message.v2.store.ExposedMessageStore
import io.opencola.relay.common.message.v2.store.Messages
import io.opencola.relay.common.policy.ExposedPolicyStore
import io.opencola.relay.common.policy.Policy
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.relay.common.policy.StoragePolicy
import io.opencola.security.initProvider
import io.opencola.storage.db.getPostgresDB
import io.opencola.storage.filestore.ContentAddressedFileStore
import io.opencola.storage.filestore.FileSystemContentAddressedFileStore
import io.opencola.storage.newSQLiteDB
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals


class ExposedMessageStoreTest {
    init {
        initProvider()
    }

    private fun newPostgresDB(): Database {
        val db = getPostgresDB("localhost", "opencola", "opencola", "test")

        transaction(db) {
            SchemaUtils.drop(Messages)
        }

        return db
    }

    private fun newExposedDB(): Database {
        return newSQLiteDB("test.db")
    }

    private fun newFileStore(): FileSystemContentAddressedFileStore {
        val fileStoreDirectory = TestApplication.getTmpDirectory("-filestore")
        return FileSystemContentAddressedFileStore(fileStoreDirectory)
    }

    private fun newExposedMessageStore(
        exposedDB: Database = newExposedDB(),
        fileStore: ContentAddressedFileStore = newFileStore(),
        policyStore: PolicyStore = ExposedPolicyStore(exposedDB, Id.new())
    ): ExposedMessageStore {
        return ExposedMessageStore(exposedDB, fileStore, policyStore)
    }

    @Test
    fun testBasicAdd() {
        testBasicAdd(newExposedMessageStore())
    }

    @Test
    fun testAddMultipleMessages() {
        testAddMultipleMessages(newExposedMessageStore())
    }

    @Test
    fun testAddWithDuplicateMessageStorageKey() {
        testAddWithDuplicateMessageStorageKey(newExposedMessageStore())
    }

    @Test
    fun testRejectMessageWhenOverQuota() {
        val policyStore = ExposedPolicyStore(newExposedDB(), Id.new(), Policy("default", storagePolicy = StoragePolicy(34)) )
        testRejectMessageWhenOverQuota(newExposedMessageStore(policyStore = policyStore))
    }

    @Test
    fun testAddAddSameMessageDifferentFrom() {
        testAddAddSameMessageDifferentFrom(newExposedMessageStore())
    }

    @Test
    fun testNoMessageStorageKey() {
        testNoMessageStorageKey(newExposedMessageStore())
    }

    @Test
    fun testSafeDeleteFromFileStore() {
        val fileStore = newFileStore()
        val messageStore = newExposedMessageStore(fileStore = fileStore)
        val message = "message".toSignedBytes()
        val messageStorageKey = MessageStorageKey.of("key")
        val from = Id.new()
        val to0 = Id.new()
        val to1 = Id.new()

        messageStore.addMessage(from, to0, messageStorageKey, dummyMessageSecretKey, message)
        messageStore.addMessage(from, to1, messageStorageKey, dummyMessageSecretKey, message)

        assertEquals(1, fileStore.enumerateFileIds().count())
        messageStore.removeMessages(to0)
        assertEquals(1, fileStore.enumerateFileIds().count())
        messageStore.removeMessages(to1)
        assertEquals(0, fileStore.enumerateFileIds().count())
    }

    @Test
    fun testUnsafeDeleteFromFileStore() {
        val fileStore = newFileStore()
        val messageStore = newExposedMessageStore(fileStore = fileStore)
        val message = "message".toSignedBytes()
        val messageStorageKey = MessageStorageKey.of("key")
        val from = Id.new()
        val to = Id.new()

        messageStore.addMessage(from, to, messageStorageKey, dummyMessageSecretKey, message)

        assertEquals(1, fileStore.enumerateFileIds().count())
        assertEquals(1, messageStore.getMessages(to).count())
        fileStore.enumerateFileIds().forEach { fileStore.delete(it) }
        assertEquals(0, fileStore.enumerateFileIds().count())
        assertEquals(0, messageStore.getMessages(to).count())

    }

    @Test
    fun testConsumeMessages() {
        val messageStore = newExposedMessageStore()
        testConsumeMessages(messageStore)
    }
}