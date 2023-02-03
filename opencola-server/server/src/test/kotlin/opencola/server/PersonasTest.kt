package opencola.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import opencola.server.model.Persona
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonasTest : ApplicationTestBase() {
    @Test
    fun testPersonas() = testApplication {
        application { configure(this) }
        val response = client.get("/personas").bodyAsText()
        val personas: List<Persona> = Json.decodeFromString(response)

        assertEquals(1, personas.size)
    }

}