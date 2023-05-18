package opencola.server

import io.opencola.model.ResourceEntity
import io.opencola.storage.entitystore.EntityStore
import opencola.server.handlers.updateComment
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PeerCommentTest : PeerNetworkTest() {
    @Test
    fun testCommentOnPeerPost(){
        val applications = getApplications(2)
        val (application0, application1) = applications
        val (server0, server1) = applications.map { getServer(it, AuthToken.encryptionParams) }

        try {
            // Start the first server and add a document
            println("Starting ${application0.config.name}")
            startServer(server0)
            val persona0 = application0.getPersonas().first()
            val resource0 = ResourceEntity(
                persona0.personaId,
                URI("http://www.opencola.io"),
                "Name",
                "Description",
                "Text",
                URI("https://opencola.io/image.png")
            )
            val entityStore0 by application0.injector.instance<EntityStore>()
            println("Adding entity")
            entityStore0.updateEntities(resource0)
            Thread.sleep(1000)

            // Start the 2nd server and comment on the doc from the first server
            println("Starting ${application1.config.name}")
            val persona1 = application1.getPersonas().first()
            val entityStore1 by application1.injector.instance<EntityStore>()
            startServer(server1)
            Thread.sleep(1500)
            val comment = updateComment(persona1, entityStore1, resource0.entityId, null, "Comment")
            val resource1 = entityStore1.getEntity(persona1.personaId, resource0.entityId) as ResourceEntity

            assertNotNull(resource1)
            assertEquals(persona1.personaId, resource1.authorityId)
            assertEquals(resource0.entityId, resource1.entityId)
            assertEquals(resource0.uri, resource1.uri)
            assertEquals(resource0.name, resource1.name)
            assertEquals(resource0.description, resource1.description)
            assertEquals(resource0.text, resource1.text)
            assertEquals(resource0.imageUri, resource1.imageUri)
            assertEquals(comment.entityId, resource1.commentIds.single())
        } finally {
            applications.forEach{ it.close() }
            server0.stop(1000, 1000)
            server1.stop(1000, 1000)
        }
    }
}