package opencola.server

import io.opencola.model.Authority
import io.opencola.model.ResourceEntity
import io.opencola.storage.EntityStore
import opencola.server.handlers.addComment
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

        // Start the first server and add a document
        logger.info { "Starting ${application0.config.name}" }
        startServer(server0)
        val authority0 by application0.injector.instance<Authority>()
        val resource0 = ResourceEntity(
            authority0.authorityId,
            URI("http://www.opencola.io"),
            "Name",
            "Description",
            "Text",
            URI("https://opencola.io/image.png")
        )
        val entityStore0 by application0.injector.instance<EntityStore>()
        logger.info { "Adding entity" }
        entityStore0.updateEntities(resource0)
        Thread.sleep(1000)

        // Start the 2nd server and comment on the doc from the first server
        logger.info { "Starting ${application1.config.name}" }
        val authority1 by application1.injector.instance<Authority>()
        val entityStore1 by application1.injector.instance<EntityStore>()
        startServer(server1)
        Thread.sleep(1500)
        val comment = addComment(authority1, entityStore1, resource0.entityId, null, "Comment")
        val resource1 = entityStore1.getEntity(authority1.authorityId, resource0.entityId) as ResourceEntity

        assertNotNull(resource1)
        assertEquals(authority1.authorityId, resource1.authorityId)
        assertEquals(resource0.entityId, resource1.entityId)
        assertEquals(resource0.uri, resource1.uri)
        assertEquals(resource0.name, resource1.name)
        assertEquals(resource0.description, resource1.description)
        assertEquals(resource0.text, resource1.text)
        assertEquals(resource0.imageUri, resource1.imageUri)
        assertEquals(comment.entityId, resource1.commentIds.single())

        server0.stop(1000,1000)
        server1.stop(1000,1000)
    }
}