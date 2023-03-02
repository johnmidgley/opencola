package opencola.server

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import opencola.server.handlers.PersonasResult
import opencola.server.viewmodel.Persona
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonasTest : ApplicationTestBase() {
    @Test
    fun testPersonas() = testApplication {
        application { configure(this) }
        val client = getClient(this)

        // Check for default Persona
        val persona = application.getPersonas().single()
        val personas = client.get("/personas").body<PersonasResult>().items
        assertEquals(1, personas.size)
        assertEquals(persona.entityId.toString(), personas[0].id)

        // Create new Persona
        val newPersona = Persona("", "Test", "", "https://test.com", "https://image", true)
        val newPersonaResponse = client.post("/personas") {
            contentType(ContentType.Application.Json)
            setBody(newPersona)
        }

        assertEquals(HttpStatusCode.Created, newPersonaResponse.status)
        val newPersona1: Persona = newPersonaResponse.body()
        assertEquals(newPersona.name, newPersona1.name)
        assertEquals(newPersona.address, newPersona1.address)
        assertEquals(newPersona.imageUri, newPersona1.imageUri)
        assertEquals(newPersona.isActive, newPersona1.isActive)

        // Read new persona
        val newPersona2: Persona = client.get("/personas/${newPersona1.id}").body()
        assertEquals(newPersona1, newPersona2)

        // Check new Persona is returned in list
        val personas1 = client.get("/personas").body<PersonasResult>().items
        assertContains(personas1, newPersona1)

        // Delete new Persona
        val deleteResponse = client.delete("/personas/${newPersona1.id}")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        // Check that created Persona no longer returned in list
        val personas2 = client.get("/personas").body<PersonasResult>().items
        assertNull(personas2.firstOrNull { it.id == newPersona1.id })
    }
}