package opencola.server

import io.ktor.routing.*
import io.ktor.server.netty.*
import kotlinx.coroutines.cancel
import opencola.core.TestApplication
import opencola.core.config.*
import opencola.core.extensions.toHexString
import opencola.core.model.Id
import org.junit.Test
import java.lang.Thread.sleep

class PeerTransactionTest {
    private val basePortNumber: Int = 6000
    private val baseConfig = TestApplication.config

    private fun getApplications(nServers: Int): List<Application> {
        val configKeyPairs = (0 until nServers)
            .map { baseConfig.setName("Server-$it") }
            .mapIndexed { i, it -> it.setServer(ServerConfig(it.server.host, basePortNumber + i)) }
            .map { it.setStoragePath(it.storage.path.resolve(it.name)) }
            .map { Pair(it, Application.getOrCreateRootPublicKey(it.storage.path, baseConfig.security)) }

        return configKeyPairs.mapIndexed { i, configKeyPair ->
            val peerConfigs = (0 until nServers)
                .filter { it != i }
                .map {
                    val (config, key) = configKeyPairs[it]
                    PeerConfig(
                        Id.ofPublicKey(key).toString(),
                        key.encoded.toHexString(),
                        config.name,
                        "${config.server.host}:${config.server.port}"
                    )
                }

            Application.instance(configKeyPair.first.setNetwork(NetworkConfig(peerConfigs)), configKeyPair.second)
        }
    }

    private fun getServers(nServers: Int): List<NettyApplicationEngine> {
        val applications = getApplications(nServers)
        return applications.map { getServer(it) }
    }


    // @Test
    fun testTransactionReplication(){
        val servers = getServers(1)
        servers.forEach{ it.start() }

        sleep(1000)
        servers.forEach{ it.stop(1000, 1000)}

        assert(true)
    }
}