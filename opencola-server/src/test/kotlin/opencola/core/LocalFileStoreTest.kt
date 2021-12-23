package opencola.core

import opencola.core.model.Id
import opencola.core.storage.LocalFileStore
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse


class LocalFileStoreTest{
    private val tempDirectory = createTempDirectory("local-file-store")
    private val localFileStore = LocalFileStore(tempDirectory)

    @Test
    fun testIdNotExists(){
        assertFalse(localFileStore.exists(Id("data".toByteArray())))
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

}