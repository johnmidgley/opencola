package opencola.server

import io.opencola.io.StdoutMonitor
import io.opencola.model.DataEntity
import io.opencola.model.ResourceEntity
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentBasedFileStore
import opencola.server.handlers.handleGetFeed
import org.junit.Test
import java.net.URI
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AttachmentsTest {
    @Test
    fun testAttachment(){
        val applications = getApplications(2)
        val (application0, application1) = applications
        val (server0, server1) = applications.map { getServer(it, AuthToken.encryptionParams) }

        try {
            // Start the first server and add a document
            println("Starting ${application0.config.name}")
            val persona0 = application0.getPersonas().first()
            startServer(server0)

            println("Starting ${application1.config.name}")
            startServer(server1)

            val app0FileStore = application0.inject<ContentBasedFileStore>()
            val attachmentData = "This is the attachment data".toByteArray()
            val attachmentId = app0FileStore.write(attachmentData)

            val dataEntity0 = DataEntity(
                persona0.personaId,
                attachmentId,
                "text/plain",
                "attachment.txt",
            )

            val entityStore0 = application0.inject<EntityStore>()

            StdoutMonitor().use {
                println("Adding attachment (data entity)")
                println(entityStore0.updateEntities(dataEntity0)!!.transaction)
                // Transaction should be indexed once locally and once remotely
                it.waitUntil("Indexed transaction")
                it.waitUntil("Indexed transaction")
                it.close()
            }

            val resource0 = ResourceEntity(
                persona0.personaId,
                URI("http://www.opencola.io"),
                "Name",
                "Description",
                "Text",
                URI("https://opencola.io/image.png")
            )
            resource0.attachmentIds = listOf(attachmentId)

            StdoutMonitor(readTimeoutMilliseconds = 5000).use {
                println("Adding entity with attachment")
                println(entityStore0.updateEntities(resource0)!!.transaction)
                // Transaction should be indexed once locally and once remotely
                it.waitUntil("Indexed transaction")
                it.waitUntil("Indexed transaction")
            }

            // Start the 2nd server and comment on the doc from the first server
            val entityStore1 = application1.inject<EntityStore>()
            val resource1 = entityStore1.getEntity(persona0.personaId, resource0.entityId) as ResourceEntity

            assertEquals(persona0.personaId, resource1.authorityId)
            assertEquals(resource0.entityId, resource1.entityId)
            assertEquals(resource0.uri, resource1.uri)
            assertEquals(resource0.name, resource1.name)
            assertEquals(resource0.description, resource1.description)
            assertEquals(resource0.text, resource1.text)
            assertEquals(resource0.imageUri, resource1.imageUri)
            assertEquals(dataEntity0.entityId, resource1.attachmentIds.single())

            val fileStore1 = application1.inject<ContentBasedFileStore>()
            val attachmentData1 = fileStore1.read(dataEntity0.entityId)
            assertNotNull(attachmentData1)
            assertContentEquals(attachmentData, attachmentData1)

            println("Removing data from file store")
            fileStore1.delete(dataEntity0.entityId)
            assertNull(fileStore1.read(dataEntity0.entityId))


            StdoutMonitor(readTimeoutMilliseconds = 5000).use {
                println("Requesting feed - should fault fill attachment in file store")
                handleGetFeed(
                    emptySet(),
                    application1.inject(),
                    application1.inject(),
                    application1.inject(),
                    application1.inject(),
                    application1.inject(),
                    null
                )
                it.waitUntil("putData: Wrote")
            }

            assertNotNull(fileStore1.read(dataEntity0.entityId))

        } finally {
            applications.forEach{ it.close() }
            server0.stop(1000, 1000)
            server1.stop(1000, 1000)
        }
    }
}