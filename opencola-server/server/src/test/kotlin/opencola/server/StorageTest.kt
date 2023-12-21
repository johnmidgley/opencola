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
        val content = "testStorage"

        try {
            client.put("/storage/$filename", content)
            val response = client.get("/storage/$filename")
            assertEquals(content, response.body())
        } finally {
            // Delete the file
            client.delete("/storage/$filename")
        }
    }
}