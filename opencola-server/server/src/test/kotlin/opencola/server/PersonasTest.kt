package opencola.server

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.testing.*
import opencola.server.model.Persona
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonasTest : ApplicationTestBase() {
    @Test
    fun testPersonas() = testApplication {
        application { configure(this) }
        val client = getClient(this)

        val persona = application.getPersonas().single()
        val personas: List<Persona> = client.get("/personas").body()
        assertEquals(1, personas.size)
        assertEquals(persona.entityId.toString(), personas[0].id)

//        val newPersona = Persona("", "Test", "", "https://test.com", "https://image", true)
//        val newPersonaResponse = client.post("/personas") {
//            contentType(ContentType.Application.Json)
//            setBody(newPersona)
//        }
//
//        assertEquals(HttpStatusCode.Created, newPersonaResponse.status)
    }

}