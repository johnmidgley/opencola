package opencola.cli

import opencola.core.TestApplication
import io.opencola.application.Application
import io.opencola.application.loadConfig
import io.opencola.model.Persona
import io.opencola.model.ResourceEntity
import io.opencola.model.Transaction
import io.opencola.security.Signator
import io.opencola.storage.AddressBook
import io.opencola.storage.EntityStore
import io.opencola.storage.EntityStore.TransactionOrder
import io.opencola.storage.ExposedEntityStore
import opencola.server.LoginCredentials
import opencola.server.getApplication
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.io.path.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CliTest {
    private fun getTmpEntityStore(application: Application): ExposedEntityStore {
        val signator by application.injector.instance<Signator>()
        val addressBook by application.injector.instance<AddressBook>()
        val db = Application.getEntityStoreDB(TestApplication.getTmpDirectory("entity-store"))
        return ExposedEntityStore(db, signator, addressBook)

    }
    @Test
    fun testExportImportRoundTrip(){
        val app = TestApplication.instance
        val authority = app.inject<AddressBook>().getAuthorities().filterIsInstance<Persona>().first()

        val entityStore0 = getTmpEntityStore(app)
        val resources = (0 until 5).map {
            ResourceEntity(authority.authorityId, URI("https://$it"))
        }
        resources.forEach{ entityStore0.updateEntities(it) }

        val transactions0 = entityStore0.getSignedTransactions(emptyList(), null, TransactionOrder.IdAscending, 100)
        assertEquals(5, transactions0.count())
        val exportPath = TestApplication.getTmpFilePath(".txs")
        exportTransactions(entityStore0, listOf(exportPath.toString()))

        val entityStore1 = getTmpEntityStore(app)
        assertEquals(0, entityStore1.getSignedTransactions(emptyList(), null, TransactionOrder.IdAscending, 100).count())
        importTransactions(entityStore1, listOf(exportPath.toString()))
        val transactions1 = entityStore1.getSignedTransactions(emptyList(), null, TransactionOrder.IdAscending, 100)
        assertContentEquals(transactions0, transactions1)
    }

    // Export transactions from .opencola/storage to transactions.bin
    fun testDumpDefaultTransactions() {
        val storagePath = Path(System.getenv("HOME")).resolve(".opencola/storage")
        val config = loadConfig(storagePath.resolve("opencola-server.yaml"))
        val app = getApplication(storagePath, config, LoginCredentials("oc", "password"))
        val entityStore = app.inject<EntityStore>()
        val authority = app.getPersonas().single()
        exportTransactions(entityStore, storagePath.resolve("transactions.bin"), listOf(authority.entityId))
    }

    // Load transactions into test storage from "transactions.bin"
    fun testLoadTransactionsToTest() {
        val storagePath = Path(System.getProperty("user.dir")).resolve("src/main/storage")
        val config = loadConfig(storagePath.resolve("opencola-server.yaml"))
        val app = getApplication(storagePath, config, LoginCredentials("oc", "password"))
        val entityStore = app.inject<EntityStore>()
        val authority = app.getPersonas().single()
        val signator = app.inject<Signator>()

        transactionsFromPath(storagePath.resolve("transactions.bin")).forEach {
            val tx = it.transaction
            val signedTransaction = Transaction(tx.id, authority.entityId, tx.transactionEntities, tx.epochSecond).sign(signator)
            entityStore.addSignedTransactions(listOf(signedTransaction))
        }
    }
}