package opencola.server

import io.opencola.event.MockEventBus
import io.opencola.io.StdoutMonitor
import io.opencola.model.DataEntity
import io.opencola.model.ResourceEntity
import io.opencola.security.generateAesKey
import io.opencola.storage.EntityStoreContext
import io.opencola.storage.MockContentBasedFileStore
import io.opencola.storage.addPersona
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentBasedFileStore
import opencola.server.handlers.Context
import opencola.server.handlers.deleteAttachment
import opencola.server.handlers.handleGetFeed
import opencola.server.handlers.saveEntity
import org.junit.Test
import java.net.URI
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AttachmentsTest {
    @Test
    fun testAttachment() {
        val applications = getApplications(2)
        val (application0, application1) = applications
        val (server0, server1) = applications.map { getServer(it, generateAesKey()) }

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
            applications.forEach { it.close() }
            server0.stop(1000, 1000)
            server1.stop(1000, 1000)
        }
    }

    @Test
    fun testDeleteAttachment() {
        val entityStoreContext = EntityStoreContext()
        val persona0 = entityStoreContext.addressBook.addPersona("Test Persona")
        val eventBus = MockEventBus()
        val fileStore = MockContentBasedFileStore()

        val data0 = "data0".toByteArray()
        val dataEntity0 = DataEntity(persona0.entityId, fileStore.write(data0), "text/plain", "dataEntity0")

        val resource0 = ResourceEntity(
            persona0.entityId,
            URI("http://www.opencola.io"),
        )

        resource0.attachmentIds = listOf(dataEntity0.entityId)
        entityStoreContext.entityStore.updateEntities(resource0, dataEntity0)

        val resource1 = entityStoreContext.entityStore.getEntity(persona0.personaId, resource0.entityId) as ResourceEntity
        assertEquals(1, resource1.attachmentIds.size)

        deleteAttachment(
            entityStoreContext.entityStore,
            entityStoreContext.addressBook,
            eventBus,
            fileStore,
            Context(persona0.entityId),
            persona0.personaId,
            resource0.entityId,
            dataEntity0.entityId
        )

        val resource2 = entityStoreContext.entityStore.getEntity(persona0.personaId, resource0.entityId) as ResourceEntity
        assertEquals(0, resource2.attachmentIds.size)
    }

    @Test
    fun testSaveEntityWithAttachments() {
        val entityStoreContext = EntityStoreContext()
        val persona0 = entityStoreContext.addressBook.addPersona("Test Persona")
        val persona1 = entityStoreContext.addressBook.addPersona("Test Persona")
        val eventBus = MockEventBus()
        val fileStore = MockContentBasedFileStore()

        val data0 = "data0".toByteArray()
        val dataEntity0 = DataEntity(persona0.entityId, fileStore.write(data0), "text/plain", "dataEntity0")
        val data1 = "data1".toByteArray()
        val dataEntity1 = DataEntity(persona0.entityId, fileStore.write(data1), "text/plain", "dataEntity1")

        val resource0 = ResourceEntity(
            persona0.entityId,
            URI("http://www.opencola.io"),
        )
        resource0.attachmentIds = listOf(dataEntity0.entityId, dataEntity1.entityId)
        entityStoreContext.entityStore.updateEntities(resource0, dataEntity0, dataEntity1)

        assertNull(entityStoreContext.entityStore.getEntity(persona1.entityId, resource0.entityId))
        assertNull(entityStoreContext.entityStore.getEntity(persona1.entityId, dataEntity0.entityId))
        assertNull(entityStoreContext.entityStore.getEntity(persona1.entityId, dataEntity1.entityId))

        saveEntity(
            entityStoreContext.entityStore,
            entityStoreContext.addressBook,
            eventBus,
            fileStore,
            Context(persona1.entityId),
            persona1,
            resource0.entityId
        )

        val resource0Persona1 = entityStoreContext.entityStore.getEntity(persona1.entityId, resource0.entityId)
        assertNotNull(resource0Persona1)
        assertEquals(persona1.entityId, resource0Persona1.authorityId)
        assertEquals(resource0.entityId, resource0Persona1.entityId)
        assertContentEquals(resource0Persona1.attachmentIds, listOf(dataEntity0.entityId, dataEntity1.entityId))

        val dataEntity0Persona1 =
            entityStoreContext.entityStore.getEntity(persona1.entityId, dataEntity0.entityId) as DataEntity
        assertNotNull(dataEntity0Persona1)
        assertEquals(persona1.entityId, dataEntity0Persona1.authorityId)
        assertEquals(dataEntity0.entityId, dataEntity0Persona1.entityId)
        assertEquals(dataEntity0.mimeType, dataEntity0Persona1.mimeType)
        assertEquals(dataEntity0.name, dataEntity0Persona1.name)

        val dataEntity1Persona1 =
            entityStoreContext.entityStore.getEntity(persona1.entityId, dataEntity1.entityId) as DataEntity
        assertNotNull(dataEntity1Persona1)
        assertEquals(persona1.entityId, dataEntity1Persona1.authorityId)
        assertEquals(dataEntity1.entityId, dataEntity1Persona1.entityId)
        assertEquals(dataEntity1.mimeType, dataEntity1Persona1.mimeType)
        assertEquals(dataEntity1.name, dataEntity1Persona1.name)
    }
}