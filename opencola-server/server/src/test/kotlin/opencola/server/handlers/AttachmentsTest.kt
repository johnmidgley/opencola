package opencola.server.handlers

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.opencola.model.Id
import io.opencola.model.ResourceEntity
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentBasedFileStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import opencola.server.ApplicationTestBase
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AttachmentsTest : ApplicationTestBase() {
    @Test
    fun testUpload() = testApplication {
        application { configure(this) }
        val persona = application.getPersonas().first()
        val file1Contents = "Test file1 contents"
        val file2Contents = "Test file2 contents"

        val boundary = "WebAppBoundary"
        val response = client.post("/upload?personaId=${persona.personaId}") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("mhtml", file1Contents.toByteArray(), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=file1")
                            append(HttpHeaders.ContentType, "text/plain")
                        })
                        append("mhtml", file2Contents.toByteArray(), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=file2")
                            append(HttpHeaders.ContentType, "text/plain")
                        })
                    },
                    boundary,
                    ContentType.MultiPart.FormData.withParameter("boundary", boundary)
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val uploadItems = Json.decodeFromString<UploadItems>(response.bodyAsText())
        assertEquals(2, uploadItems.items.size)
        val file1 = application.inject<ContentBasedFileStore>().read(Id.Factory.decode(uploadItems.items[0].id))
        assertNotNull(file1)
        assertEquals(file1Contents, file1Contents)
        val file2 = application.inject<ContentBasedFileStore>().read(Id.Factory.decode(uploadItems.items[1].id))
        assertNotNull(file2)
        assertEquals(file2Contents, file2Contents)
    }

    @Test
    fun testAttach() = testApplication {
        application { configure(this) }
        val entityStore = application.inject<EntityStore>()
        val persona = application.getPersonas().first()
        val resource = ResourceEntity(persona.personaId, URI("https://opencola.io/${Id.new()}"))
            .also { entityStore.updateEntities(it) }
        val file1Contents = "Test file1 contents"
        val file2Contents = "Test file2 contents"

        val boundary = "WebAppBoundary"
        val response = client.post("/entity/${resource.entityId}/attachment?personaId=${persona.personaId}") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("mhtml", file1Contents.toByteArray(), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=file1")
                            append(HttpHeaders.ContentType, "text/plain")
                        })
                        append("mhtml", file2Contents.toByteArray(), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=file2")
                            append(HttpHeaders.ContentType, "text/plain")
                        })
                    },
                    boundary,
                    ContentType.MultiPart.FormData.withParameter("boundary", boundary)
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val entityResult = Json.decodeFromString<EntityResult>(response.bodyAsText())
        val attachActions = entityResult.activities
            .flatMap { it.actions }
            .filter { it.type == "attach" }
        assertEquals(2, attachActions.size)
        val file1 = application.inject<ContentBasedFileStore>().read(Id.Factory.decode(attachActions[0].id!!))
        assertNotNull(file1)
        assertEquals(file1Contents, file1Contents)
        val file2 = application.inject<ContentBasedFileStore>().read(Id.Factory.decode(attachActions[1].id!!))
        assertNotNull(file2)
        assertEquals(file2Contents, file2Contents)
    }
}