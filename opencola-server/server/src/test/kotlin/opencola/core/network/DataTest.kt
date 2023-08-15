package opencola.core.network

import io.opencola.io.StdoutMonitor
import io.opencola.io.waitForStdout
import io.opencola.model.DataEntity
import io.opencola.network.NetworkNode
import io.opencola.network.message.GetDataMessage
import io.opencola.security.generateAesKey
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentBasedFileStore
import opencola.server.getApplications
import opencola.server.getServer
import opencola.server.startServer
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

class DataTest {
    @Test
    fun testGetPutData() {
        val applications = getApplications(2)
        val (app0, app1) = applications
        val (server0, server1) = applications.map { getServer(it, generateAesKey()) }

        try {
            val app0Persona =
                app0.inject<AddressBook>().getEntries().filterIsInstance<PersonaAddressBookEntry>().first()
            val app1Persona =
                app1.inject<AddressBook>().getEntries().filterIsInstance<PersonaAddressBookEntry>().first()

            println("Waiting for servers to finish requesting transactions")
            waitForStdout("Completed requesting transactions") { startServer(server0) }
            waitForStdout("Completed requesting transactions") { startServer(server1) }

            println("Adding data to app0")
            val fileStore0 = app0.inject<ContentBasedFileStore>()
            val entityStore0 = app0.inject<EntityStore>()
            val data = "hello".toByteArray()
            val dataId = fileStore0.write(data)

            println("Waiting for apps to index transaction")
            StdoutMonitor().use { monitor ->
                entityStore0.updateEntities(DataEntity(app0Persona.entityId, dataId, "application/octet-stream"))
                monitor.waitUntil("Indexed transaction", 3000)
                monitor.waitUntil("Indexed transaction", 3000)
            }

            println("Requesting data from app1")
            val networkNode1 = app1.inject<NetworkNode>()
            val message = GetDataMessage(dataId)
            waitForStdout("RequestRouting: putData") {
                networkNode1.sendMessage(app1Persona.entityId, app0Persona.entityId, message)
            }

            assertNotNull(app1.inject<EntityStore>().getEntity(app0Persona.entityId, dataId) as? DataEntity)
            assertContentEquals(data, app1.inject<ContentBasedFileStore>().read(dataId))
        } finally {
            applications.forEach { it.close() }
            server0.stop(1000, 1000)
            server1.stop(1000, 1000)
        }
    }
}