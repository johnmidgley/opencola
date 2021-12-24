package opencola.core

import opencola.core.model.Id
import opencola.core.storage.LocalFileStore
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class LocalFileStoreTest{
    private val tempDirectory = createTempDirectory("local-file-store")
    private val localFileStore = LocalFileStore(tempDirectory)

    @Test
    fun testExists(){
        val testString = "Test file data"
        val data = testString.toByteArray()
        val id = Id(data)

        assertFalse(localFileStore.exists(id))
        localFileStore.write(data)
        assertTrue(localFileStore.exists(id))
    }

    @Test
    fun testWriteByteArray(){
        val testString = "Test file data"
        val data = testString.toByteArray()
        val id = localFileStore.write(data)

        assertEquals(id, Id(data))

        val data1 = localFileStore.read(id)
        assert(data.contentEquals(data1))
    }

    @Test
    fun testWriteFromInputStream(){
        val testString = "Test file data"
        val data = testString.toByteArray()

        val id = localFileStore.write(data.inputStream())
        assertEquals(id, Id(data))

        val data1 = localFileStore.read(id)
        assert(data.contentEquals(data1))

        // Write again - this should be idempotent
        val id2 = localFileStore.write(data.inputStream())
        assertEquals(id2, id)

        // This time, read from input stream
        val data2 = localFileStore.getInputStream(id).readAllBytes()
        assert(data.contentEquals(data2))
    }

}