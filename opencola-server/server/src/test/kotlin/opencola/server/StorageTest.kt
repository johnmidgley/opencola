package opencola.server

import io.ktor.client.call.*
import io.ktor.server.testing.*
import junit.framework.TestCase.assertEquals
import kotlin.test.Test

class StorageTest : ApplicationTestBase() {
    @Test
    fun testStorage() = testApplication {
        application { configure(this) }
        val client = JsonClient(this)

        val filename = "testStorage.txt"

        try {
            val content0 = "testStorage"
            client.put("/storage/$filename", content0)
            val response = client.get("/storage/$filename")
            assertEquals(content0, response.body())

            // Test overwriting
            val content1 = "testStorage1"
            client.put("/storage/$filename", content1)
            val response1 = client.get("/storage/$filename")
            assertEquals(content1, response1.body())
        } finally {
            // Delete the file
            client.delete("/storage/$filename")
        }
    }
}