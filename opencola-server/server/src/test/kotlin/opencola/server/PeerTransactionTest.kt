package opencola.server

import io.opencola.model.ResourceEntity
import io.opencola.storage.EntityStore
import opencola.server.handlers.handleSearch
import org.junit.Test
import org.kodein.di.instance
import java.lang.Thread.sleep
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PeerTransactionTest : PeerNetworkTest() {
    @Test
    fun testTransactionReplication() {
        val applications = getApplications(2)
        val (application0, application1) = applications
        val (server0, server1) = applications.map { getServer(it, AuthToken.encryptionParams) }

        try {
            // Start first server and add a resource to the store
            startServer(server0)
            val authority0 = application0.getPersonas().first()
            val resource0 =
                ResourceEntity(authority0.authorityId, URI("http://www.opencola.org"), "document 1", text = "stuff")
            val entityStore0 by application0.injector.instance<EntityStore>()
            entityStore0.updateEntities(resource0)
            // Verify retrieval of transaction on startup via search

            startServer(server1)
            sleep(1000) // TODO Bad - after event bus is implemented, trigger off events, rather than waiting for sync
            val results0 = handleSearch(application1.inject(), application1.inject(), "stuff")
            assert(results0.matches.size == 1)
            assert(results0.matches[0].name == resource0.name)

            // Verify entity update triggers live replication
            val resource1 = ResourceEntity(
                authority0.authorityId,
                URI("http://www.opencola.org/page"),
                "document 2",
                text = "other stuff"
            )
            entityStore0.updateEntities(resource1)
            sleep(1500)
            val results1 = handleSearch(application1.inject(), application1.inject(), "other")
            assert(results1.matches.size == 1)
            assert(results1.matches[0].name == resource1.name)

            entityStore0.deleteEntity(authority0.authorityId, resource1.entityId)
            sleep(1000)
            val results2 = handleSearch(application1.inject(), application1.inject(), "other")
            assert(results2.matches.isEmpty())
        } finally {
            server0.stop(1000, 1000)
            server1.stop(1000, 1000)
        }
    }

    @Test
    fun testRequestOnlineTrigger() {
        val applications = getApplications(2)
        val (application0, application1) = applications
        val (server0, server1) = applications.map { getServer(it, AuthToken.encryptionParams) }

        try {
            // Start the first server and add a document
            logger.info { "Starting ${application0.config.name}" }
            startServer(server0)
            val authority0 = application0.getPersonas().first()
            val resource0 =
                ResourceEntity(authority0.authorityId, URI("http://www.opencola.org"), "document 1", text = "stuff")
            val entityStore0 by application0.injector.instance<EntityStore>()
            logger.info { "Adding entity" }
            entityStore0.updateEntities(resource0)
            sleep(1000)

            // Stop the server so the transaction won't be available when the 2nd server starts up
            logger.info { "Stopping ${application0.config.name}" }
            server0.stop(1000, 1000)

            // Start the 2nd server and add a doc to it. This should trigger a request for transactions that will fail, since
            // the first server is not running
            logger.info { "Starting ${application1.config.name}" }
            startServer(server1)
            sleep(1000) // TODO Bad - after event bus is implemented, trigger off events, rather than waiting for sync

            // Now start up the first server again. This will trigger call get transactions to server 1, which should trigger
            // it to grab the missing transaction
            logger.info { "Re-starting ${application0.config.name}" }
            val server0restart = getServer(application0, AuthToken.encryptionParams)
            startServer(server0restart)
            sleep(2000)

            logger.info { "Searching ${application1.config.name}" }
            val results0 = handleSearch(application1.inject(), application1.inject(), "stuff")
            assert(results0.matches.size == 1)
            assert(results0.matches[0].name == resource0.name)
        } finally {
            server0.stop(1000, 1000)
            server1.stop(1000, 1000)
        }
    }

    @Test
    fun testConnectAndBidirectionalReplicate(){
        val server0 = getApplicationNode().also { it.start() }
        val server1 = getApplicationNode()// .also { it.start() }

        try {
            val app0 = server0.application
            val app1 = server1.application

            // Add item to server0
            val authorityId0 = app0.getPersonas().single().entityId
            val resourceEntity0 = ResourceEntity(authorityId0, URI("https://opencola.io"), "OpenCola From Server 0", "OpenCola Website 0")
            val entityStore0 = app0.inject<EntityStore>()
            entityStore0.updateEntities(resourceEntity0)

            // Add server1 as peer to server0 when server 1 is offline
            server0.updatePeer(server0.postInviteToken(server1.getInviteToken()))

            // Start server1
            server1.start()

            // Add server0 as peer to server1
            server1.updatePeer(server1.postInviteToken(server0.getInviteToken()))

            // Now add something to server 1
            val authorityId1 = app1.getPersonas().single().entityId
            val resourceEntity1 = ResourceEntity(authorityId1, URI("https://opencola.io/"), "OpenCola From Server 1", "OpenCola Website 1")
            val entityStore1 = app1.inject<EntityStore>()
            entityStore1.updateEntities(resourceEntity1)

            // Wait for request errors to roll through
            sleep(2000)

            val resource1FromServer0 = entityStore0.getEntity(resourceEntity1.authorityId, resourceEntity1.entityId) as? ResourceEntity
            assertNotNull(resource1FromServer0)
            assertEquals(resourceEntity1.name, resource1FromServer0.name)

            val resource0FromServer1 = entityStore1.getEntity(resourceEntity0.authorityId, resourceEntity0.entityId) as? ResourceEntity
            assertNotNull(resource0FromServer1)
            assertEquals(resourceEntity0.name, resource0FromServer1.name)
        } finally {
            server0.stop()
            server1.stop()
        }
    }
}