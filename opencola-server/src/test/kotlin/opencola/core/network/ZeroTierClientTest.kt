package opencola.core.network

import kotlinx.coroutines.runBlocking
import opencola.core.network.zerotier.Client
import opencola.core.network.zerotier.Network
import opencola.core.network.zerotier.NetworkConfig
import org.junit.Test
import java.time.Instant

const val authToken = ""
class ZeroTierClientTest {
    // @Test
    fun testGetNetworks(){
        var zeroTierClient = Client(authToken)
        val networks = runBlocking { zeroTierClient.getNetworks() }
        println(networks)
    }

    // @Test
    fun testCreateNetwork() {
        val epochSecond = Instant.now().epochSecond
        val networkConfig = NetworkConfig.forCreate(name = "Test Network: $epochSecond ", private = true)
        val network = Network.forCreate(networkConfig, "Test Description")
        val zeroTierClient = Client(authToken)
        val response = runBlocking{ zeroTierClient.createNetwork(network) }
        println(response)
    }

    // @Test
    fun testGetNetworkMembers(){
        val zeroTierClient = Client(authToken)
        val response = runBlocking { zeroTierClient.getNetworkMembers("c7c8172af10fb06c") }
        println(response)
    }
}