package io.opencola.relay.common.message.store

import io.opencola.application.TestApplication
import io.opencola.relay.common.message.v2.store.ExposedMessageStore
import io.opencola.security.initProvider
import io.opencola.storage.db.getSQLiteDB
import io.opencola.storage.filestore.ContentAddressedFileStore
import io.opencola.storage.filestore.FileSystemContentAddressedFileStore
import org.jetbrains.exposed.sql.Database
import kotlin.test.Test


class ExposedMessageStoreTest {
    init {
        initProvider()
    }

    private fun newExposedDB(): Database {
        val dbDirectory = TestApplication.getTmpDirectory("-db")
        return getSQLiteDB(dbDirectory.resolve("test.db"))
    }

    private fun newFileStore(): ContentAddressedFileStore {
        val fileStoreDirectory = TestApplication.getTmpDirectory("-filestore")
        return FileSystemContentAddressedFileStore(fileStoreDirectory)
    }

    private fun newExposedMessageStore(
        maxStoredBytesPerConnection: Int = 1024 * 1024 * 50,
        exposedDB: Database = newExposedDB(),
        fileStore: ContentAddressedFileStore = newFileStore()
    ): ExposedMessageStore {
        return ExposedMessageStore(exposedDB, fileStore, maxStoredBytesPerConnection)
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
        testRejectMessageWhenOverQuota(newExposedMessageStore(34))
    }

    @Test
    fun testAddAddSameMessageDifferentFrom() {
        testAddAddSameMessageDifferentFrom(newExposedMessageStore())
    }

    @Test
    fun testNoMessageStorageKey() {
        testNoMessageStorageKey(newExposedMessageStore())
    }
}