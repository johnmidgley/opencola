package opencola.server

import io.ktor.server.application.*
import io.ktor.server.netty.*
import mu.KotlinLogging
import io.opencola.application.TestApplication
import io.opencola.application.Application
import io.opencola.io.StdoutMonitor
import io.opencola.model.ResourceEntity
import io.opencola.storage.EntityStore
import io.opencola.storage.waitForAddressBookAdd
import opencola.core.network.ApplicationNode
import opencola.server.handlers.inviteTokenToPeer
import java.net.URI
import kotlin.test.assertEquals

private var nextServerNum = 0

open class PeerNetworkTest {
    protected val logger = KotlinLogging.logger("PeerTransactionTest")
    private val basePortNumber: Int = 6001

    protected fun getApplications(nServers: Int): List<Application> {
        return opencola.core.config.getApplications(
            TestApplication.storagePath,
            TestApplication.config,
            basePortNumber,
            nServers
        )
    }

    protected fun startServer(engine: NettyApplicationEngine) {
        // TODO: This is horrible. Figure out how to do this properly with suspend / coroutine / etc..
        var started = false
        engine.environment.monitor.subscribe(ApplicationStarted) { started = true }
        engine.start()
        while (!started) {
            Thread.sleep(100)
        }
    }

    protected fun getApplicationNode(): ApplicationNode {
        return ApplicationNode.getNode(nextServerNum++, false, ApplicationNode.getBaseConfig())
    }

    private fun addPeer(applicationNode: ApplicationNode, peerApplicationNode: ApplicationNode) {
        val app = applicationNode.application
        val inviteToken = peerApplicationNode.getInviteToken()
        val peer = inviteTokenToPeer(app.inject(), inviteToken)
        applicationNode.updatePeer(peer)
    }

    fun testConnectAndReplicate(application0: ApplicationNode, application1: ApplicationNode) {
        val stdoutMonitor = StdoutMonitor(readTimeoutMilliseconds = 5000)
        val waitUntil = { text: String -> stdoutMonitor.waitUntil(text) }

        try {
            println("Adding entity to application0")
            val authority0 = application0.application.getPersonas().single()
            val resource0 = ResourceEntity(authority0.entityId, URI("https://resource0"), "Resource0")
            val entityStore0 = application0.application.inject<EntityStore>().also { it.updateEntities(resource0) }

            println("Adding entity to application1")
            val authority1 = application1.application.getPersonas().single()
            val resource1 = ResourceEntity(authority1.entityId, URI("https://resource1"), "Resource1")
            val entityStore1 = application1.application.inject<EntityStore>().also { it.updateEntities(resource1) }

            println("Adding application1 as peer to application0")
            // Note this will trigger an expected error in the logs, since it will trigger a transaction request, but
            // app0 isn't known to app1 yet
            addPeer(application0, application1)
            stdoutMonitor.waitForAddressBookAdd(authority0.entityId, authority1.entityId)
            println("Adding application0 as peer to application1")
            addPeer(application1, application0)

            // Connection should trigger two index operations from transaction sharing
            println("Waiting for index operations to complete")
            waitUntil ("LuceneSearchIndex: Indexing")
            waitUntil("LuceneSearchIndex: Indexing")

            println("Verifying replication")
            assertEquals(entityStore0.getEntity(resource1.authorityId, resource1.entityId)?.name, resource1.name)
            assertEquals(entityStore1.getEntity(resource0.authorityId, resource0.entityId)?.name, resource0.name)
        } finally {
            println("Closing resources")
            stdoutMonitor.close()
            application0.stop()
        }
    }
}