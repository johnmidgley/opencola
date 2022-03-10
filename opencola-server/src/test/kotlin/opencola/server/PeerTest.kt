package opencola.server

import opencola.core.TestApplication
import opencola.core.config.Application
import opencola.core.config.Network
import opencola.core.config.Peer
import opencola.core.config.Server
import opencola.core.model.Authority
import opencola.core.model.ResourceEntity
import opencola.core.security.KeyStore
import opencola.core.security.Signator
import opencola.core.storage.NetworkedEntityStore
import opencola.core.storage.SimpleEntityStore
import opencola.service.search.SearchService
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals


class PeerTest {
    val app = TestApplication.instance

    @Test
    fun testTransactionBroadcast(){
        // Start "local" server
        val server = getServer(Server("0.0.0.0", 5795))
        val engine = server.start()

        // Create peer networked entity store
        val config = TestApplication.config
        val storagePath = TestApplication.testRunStoragePath.resolve("peer").createDirectories()
        val authority = Authority(Application.getOrCreateRootPublicKey(storagePath, TestApplication.config))
        val keyStore = KeyStore(storagePath.resolve(config.security.keystore.name), config.security.keystore.password)
        val signator = Signator(keyStore)
        val entityStore = SimpleEntityStore(TestApplication.getTmpFilePath(".txs"), authority, signator)
        val networkedEntityStore = NetworkedEntityStore(entityStore, Network(listOf(Peer("test-id", "test-peer", "0.0.0.0:5795"))))

        // Add entity to the peer store
        val resourceName = "Opencola stuff"
        val resourceEntity = ResourceEntity(authority.authorityId, URI("https://opeccola.org"), name = "Opencola stuff")
        networkedEntityStore.commitChanges(resourceEntity)

        // Verify that entity is searchable in the "local" application
        val searchService by TestApplication.instance.injector.instance<SearchService>()
        val results = searchService.search("stuff")

        assertEquals(1, results.matches.count())
        assertEquals(resourceName, results.matches[0].name)

        engine.stop(1000, 1000)
    }
}