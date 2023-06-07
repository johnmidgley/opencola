package opencola.server

import io.opencola.io.StdoutMonitor
import io.opencola.io.waitForStdout
import io.opencola.model.ResourceEntity
import io.opencola.storage.entitystore.EntityStore
import opencola.server.handlers.handleSearch
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PeerTransactionTest {
    @Test
    fun testTransactionReplication() {
        val applications = getApplications(2)
        val (application0, application1) = applications
        val (server0, server1) = applications.map { getServer(it, AuthToken.encryptionParams) }

        try {
            // Start first server and add a resource to the store
            startServer(server0)
            val persona = application0.getPersonas().first()
            val resource0 =
                ResourceEntity(persona.personaId, URI("http://www.opencola.org"), "document 1", text = "stuff")
            val entityStore0 by application0.injector.instance<EntityStore>()

            println("Updating resource0")
            // TODO: Could make set of functions: waitForBroadcast, waitForIndexing, etc.
            waitForStdout("Broadcasting request: PUT_TRANSACTION") { entityStore0.updateEntities(resource0) }

            println("Starting server1")
            waitForStdout("Indexed authorityId:") { startServer(server1) }

            println("Searching")
            val results0 = handleSearch(application1.inject(), application1.inject(), emptySet(), "stuff")
            assert(results0.matches.size == 1)
            assert(results0.matches[0].name == resource0.name)

            // Verify entity update triggers live replication
            val resource1 = ResourceEntity(
                persona.personaId,
                URI("http://www.opencola.org/page"),
                "document 2",
                text = "other stuff"
            )

            println("Updating resource1")
            StdoutMonitor(readTimeoutMilliseconds = 3000).use {
                entityStore0.updateEntities(resource1)
                // Wait for local and remote indexing
                it.waitUntil("Indexed transaction:")
                it.waitUntil("Indexed transaction:")
            }

            println("Searching")
            val results1 = handleSearch(application1.inject(), application1.inject(), emptySet(), "other")
            assert(results1.matches.size == 1)
            assert(results1.matches[0].name == resource1.name)


            StdoutMonitor(readTimeoutMilliseconds = 3000).use {
                entityStore0.deleteEntities(persona.personaId, resource1.entityId)
                // Wait for local and remote indexing
                it.waitUntil("Indexed transaction:")
                it.waitUntil("Indexed transaction:")
            }

            println("Searching")
            val results2 = handleSearch(application1.inject(), application1.inject(), emptySet(), "other")
            assert(results2.matches.isEmpty())
        } finally {
            applications.forEach { it.close() }
            server0.stop(1000, 1000)
            server1.stop(1000, 1000)
        }
    }

    @Test
    fun testRequestOnlineTrigger() {
        val applications = getApplications(2)
        val (application0, application1) = applications
        val (server0, server1) = applications.map { getServer(it, AuthToken.encryptionParams) }
        val server0restart = getServer(application0, AuthToken.encryptionParams)

        try {
            // Start the first server and add a document
            println("Starting ${application0.config.name}")
            startServer(server0)
            val persona = application0.getPersonas().first()
            val resource0 =
                ResourceEntity(persona.personaId, URI("http://www.opencola.org"), "document 1", text = "stuff")
            val entityStore0 by application0.injector.instance<EntityStore>()

            println("Adding entity")
            waitForStdout("Sending request SignedMessage") { entityStore0.updateEntities(resource0) }

            // Stop the server so the transaction won't be available when the 2nd server starts up
            println("Stopping ${application0.config.name}")
            server0.stop(1500, 1500)

            // Start the 2nd server and add a doc to it. This should trigger a request for transactions that will fail, since
            // the first server is not running
            println("Starting ${application1.config.name}")
            waitForStdout("appears to be offline.") { startServer(server1) }

            // Now start up the first server again. This will trigger call get transactions to server 1, which should trigger
            // it to grab the missing transaction
            println("Re-starting ${application0.config.name}")
            waitForStdout("LuceneSearchIndex: Indexed") { startServer(server0restart) }

            println("Searching ${application1.config.name}")
            val results0 = handleSearch(application1.inject(), application1.inject(), emptySet(), "stuff")
            assert(results0.matches.size == 1)
            assert(results0.matches[0].name == resource0.name)
        } finally {
            applications.forEach { it.close() }
            server0.stop(1000, 1000)
            server1.stop(1000, 1000)
            server0restart.stop(1000, 1000)
        }
    }

    @Test
    fun testConnectAndBidirectionalReplicate() {
        val server0 = getApplicationNode().also { it.start() }
        val server1 = getApplicationNode()// .also { it.start() }

        try {
            val app0 = server0.application
            val app1 = server1.application

            // Add item to server0
            val authorityId0 = app0.getPersonas().single().entityId
            val app0Resource0 =
                ResourceEntity(authorityId0, URI("https://opencola.io/resource0"), "app0resource0")
            val entityStore0 = app0.inject<EntityStore>()

            println("Adding app0Resource0")
            waitForStdout("Indexed transaction:") { entityStore0.updateEntities(app0Resource0) }

            val app0Resource1 =
                ResourceEntity(authorityId0, URI("https://opencola.io/resource1"), "app0resource1")
            println("Adding app0Resource1")
            waitForStdout("Indexed transaction:") { entityStore0.updateEntities(app0Resource1) }

            println("Add server1 as peer to server0 when server 1 is offline")
            waitForStdout("Completed requesting transactions") {
                server0.updatePeer(server0.postInviteToken(server1.getInviteToken()))
            }

            println("Starting server1")
            waitForStdout("NodeStarted") { server1.start() }

            println("Add server0 as peer to server1")
            // Add server0 as peer to server1
            StdoutMonitor(readTimeoutMilliseconds = 3000).use {
                server1.updatePeer(server1.postInviteToken(server0.getInviteToken()))
                // Wait for 2 transactions to be indexed
                it.waitUntil("Indexed transaction")
                it.waitUntil("Indexed transaction")
            }

            println("Adding app1Resource1 to server1")
            val authorityId1 = app1.getPersonas().single().entityId
            val app1Resource1 = ResourceEntity(
                authorityId1,
                URI("https://opencola.io/"),
                "app1Resource1",
            )
            val entityStore1 = app1.inject<EntityStore>()

            StdoutMonitor(readTimeoutMilliseconds = 3000).use {
                entityStore1.updateEntities(app1Resource1)
                // Wait for local and remote indexing
                it.waitUntil("Indexed transaction")
                it.waitUntil("Indexed transaction")
            }

            println("Getting entity from server0")
            val app1Resource1FromServer0 =
                entityStore0.getEntity(app1Resource1.authorityId, app1Resource1.entityId) as? ResourceEntity
            assertNotNull(app1Resource1FromServer0)
            assertEquals(app1Resource1.name, app1Resource1FromServer0.name)

            val app0Resource0FromServer1 =
                entityStore1.getEntity(app0Resource0.authorityId, app0Resource0.entityId) as? ResourceEntity
            assertNotNull(app0Resource0FromServer1)
            assertEquals(app0Resource0.name, app0Resource0FromServer1.name)

            val app0Resource1FromServer1 =
                entityStore1.getEntity(app0Resource1.authorityId, app0Resource1.entityId) as? ResourceEntity
            assertNotNull(app0Resource1FromServer1)
            assertEquals(app0Resource1.name, app0Resource1FromServer1.name)
        } finally {
            server0.application.close()
            server1.application.close()
            server0.stop()
            server1.stop()
        }
    }
}