package opencola.cli

import opencola.core.TestApplication
import opencola.core.model.Authority
import opencola.core.model.ResourceEntity
import opencola.core.storage.EntityStore
import opencola.core.storage.EntityStore.TransactionOrder
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.test.assertContentEquals

class CliTest {
    @Test
    fun testExportImportRoundTrip(){
        val application = TestApplication.instance
        val authority by application.injector.instance<Authority>()
        val entityStore by application.injector.instance<EntityStore>()

        val resources = (0 until 5).map {
            ResourceEntity(authority.authorityId, URI("https://$it"))
        }

        entityStore.resetStore()
        resources.forEach{ entityStore.updateEntities(it) }
        val transactions0 = entityStore.getSignedTransactions(emptyList(), null, TransactionOrder.IdAscending, 100)

        val exportPath0 = TestApplication.getTmpFilePath(".txs")
        exportTransactions(application, listOf(exportPath0.toString()))

        entityStore.resetStore()
        importTransactions(application, listOf(exportPath0.toString()))
        val transactions1 = entityStore.getSignedTransactions(emptyList(), null, TransactionOrder.IdAscending, 100)

        assertContentEquals(transactions0, transactions1)
    }
}