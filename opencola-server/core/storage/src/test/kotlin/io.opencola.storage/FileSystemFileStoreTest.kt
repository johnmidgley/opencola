package io.opencola.storage

import io.opencola.application.TestApplication
import io.opencola.io.isDirectoryEmpty
import io.opencola.model.Id
import io.opencola.storage.filestore.FileSystemContentAddressedFileStore
import kotlin.test.*


class FileSystemFileStoreTest {
    private fun getLocalFileStore(name: String) : FileSystemContentAddressedFileStore {
        return FileSystemContentAddressedFileStore(TestApplication.getTmpFilePath(name))
    }

    @Test
    fun testExists() {
        val fileStore = getLocalFileStore("testExists")
        val testString = "Test file data"
        val data = testString.toByteArray()
        val id = Id.ofData(data)

        assertFalse(fileStore.exists(id))
        fileStore.write(data)
        assertTrue(fileStore.exists(id))
    }

    @Test
    fun testWriteByteArray() {
        val fileStore = getLocalFileStore("testWriteByteArray")
        val testString = "Test file data"
        val data = testString.toByteArray()
        val id = fileStore.write(data)

        assertEquals(id, Id.ofData(data))

        val data1 = fileStore.read(id)
        assert(data.contentEquals(data1))
    }

    @Test
    fun testWriteFromInputStream() {
        val fileStore = getLocalFileStore("testWriteFromInputStream")
        val testString = "Test file data"
        val data = testString.toByteArray()

        val id = data.inputStream().use { fileStore.write(it) }
        assertEquals(id, Id.ofData(data))

        val data1 = fileStore.read(id)
        assert(data.contentEquals(data1))

        // Write again - this should be idempotent
        val id2 = data.inputStream().use { fileStore.write(it) }
        assertEquals(id2, id)

        // This time, read from input stream
        val data2 = fileStore.getInputStream(id).use { it!!.readAllBytes() }
        assert(data.contentEquals(data2))
    }

    @Test
    fun testDelete() {
        val path = TestApplication.getTmpFilePath("testDelete")
        val fileStore = FileSystemContentAddressedFileStore(path)
        val testString = "Test file data"
        val data = testString.toByteArray()
        val id = fileStore.write(data)

        assertFalse(isDirectoryEmpty(path))
        assertTrue(fileStore.exists(id))
        fileStore.delete(id)
        assertFalse(fileStore.exists(id))
        assertTrue(isDirectoryEmpty(path))
    }

    @Test
    fun testGetDataIds() {
        val fileStore = getLocalFileStore("testGetFileIds")

        val data1 = "data1".toByteArray()
        val id1 = fileStore.write(data1)

        val data2 = "data2".toByteArray()
        val id2 = fileStore.write(data2)

        val data3 = "data3".toByteArray()
        val id3 = fileStore.write(data3)

        val ids = fileStore.getDataIds().toList()
        assert(ids.size == 3)
        assertContains(ids, id1)
        assertContains(ids, id2)
        assertContains(ids, id3)
    }
}