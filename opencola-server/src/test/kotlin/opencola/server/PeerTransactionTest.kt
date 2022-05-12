package opencola.server

import opencola.core.model.Authority
import opencola.core.model.ResourceEntity
import opencola.core.storage.EntityStore
import opencola.service.search.SearchService
import org.junit.Test
import org.kodein.di.instance
import java.lang.Thread.sleep
import java.net.URI

class PeerTransactionTest : PeerTest() {
    @Test
    fun testTransactionReplication(){
        val applications = getApplications(2)
        val (application0, application1) = applications
        val (server0, server1) = applications.map { getServer(it) }

        // Start first server and add a resource to the store
        startServer(server0)
        val authority0 by application0.injector.instance<Authority>()
        val resource0 = ResourceEntity(authority0.authorityId, URI("http://www.opencola.org"), "document 1", text="stuff")
        val entityStore0 by application0.injector.instance<EntityStore>()
        entityStore0.updateEntities(resource0)

        // Verify retrieval of transaction on startup via search
        startServer(server1)
        sleep(1000) // TODO Bad - after event bus is implemented, trigger off events, rather than waiting for sync
        val searchService1 by application1.injector.instance<SearchService>()
        val results0 = searchService1.search("stuff")
        assert(results0.matches.size == 1)
        assert(results0.matches[0].name == resource0.name)

        // Verify entity update triggers live replication
        val resource1 = ResourceEntity(authority0.authorityId, URI("http://www.opencola.org/page"), "document 2", text = "other stuff")
        entityStore0.updateEntities(resource1)
        sleep(1000)
        val results1 = searchService1.search("other")
        assert(results1.matches.size == 1)
        assert(results1.matches[0].name == resource1.name)

        entityStore0.deleteEntity(authority0.authorityId, resource1.entityId)
        sleep(1000)
        val results2 = searchService1.search("other")
        assert(results2.matches.isEmpty())

        server0.stop(1000,1000)
        server1.stop(1000,1000)
    }

    @Test
    fun testRequestOnlineTrigger(){
        val applications = getApplications(2)
        val (application0, application1) = applications
        val (server0, server1) = applications.map { getServer(it) }

        // Start the first server and add a document
        logger.info { "Starting ${application0.config.name}" }
        startServer(server0)
        val authority0: Authority by application0.injector.instance<Authority>()
        val resource0 = ResourceEntity(authority0.authorityId, URI("http://www.opencola.org"), "document 1", text="stuff")
        val entityStore0 by application0.injector.instance<EntityStore>()
        logger.info { "Adding entity" }
        entityStore0.updateEntities(resource0)
        sleep(1000)

        // Stop the server so the transaction won't be available when the 2nd server starts up
        logger.info { "Stopping ${application0.config.name}" }
        server0.stop(1000,1000)

        // Start the 2nd server and add a doc to it. This should trigger a request for transactions that will fail, since
        // the first server is not running
        logger.info { "Starting ${application1.config.name}" }
        startServer(server1)
        sleep(1000) // TODO Bad - after event bus is implemented, trigger off events, rather than waiting for sync

        // Now start up the first server again. This will trigger call get transactions to server 1, which should trigger
        // it to grab the missing transaction
        logger.info { "Re-starting ${application0.config.name}" }
        val server0restart = getServer(application0)
        startServer(server0restart)
        sleep(2000)

        logger.info { "Searching ${application1.config.name}" }
        val searchService1 by application1.injector.instance<SearchService>()
        val results0 = searchService1.search("stuff")
        assert(results0.matches.size == 1)
        assert(results0.matches[0].name == resource0.name)

        server0.stop(1000,1000)
        server1.stop(1000,1000)
    }
}