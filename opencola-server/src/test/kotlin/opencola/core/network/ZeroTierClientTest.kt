package opencola.core.network

import kotlinx.coroutines.runBlocking
import opencola.core.network.zerotier.*
import org.junit.Test
import java.time.Instant

const val authToken = ""
class ZeroTierClientTest {
    // @Test
    fun testCreateNetwork() {
        val epochSecond = Instant.now().epochSecond
        val networkConfig = NetworkConfig.forCreate(
            name = "Test Network: $epochSecond ",
            private = true,
            routes = listOf(Route("172.27.0.0/16")),
            // v4AssignMode = IPV4AssignMode(true),
            // ipAssignmentPools = listOf(IPRange("10.243.0.1", "10.243.255.254"))
        )
        val network = Network.forCreate(networkConfig, "Test Description")
        val zeroTierClient = Client(authToken)
        val response = runBlocking{ zeroTierClient.createNetwork(network) }
        println(response)
    }

    // @Test
    fun testGetNetworks(){
        val zeroTierClient = Client(authToken)
        val networks = runBlocking { zeroTierClient.getNetworks() }
        println(networks)
    }

    // @Test
    fun testGetNetwork(){
        val zeroTierClient = Client(authToken)
        val network = runBlocking { zeroTierClient.getNetwork("") }
        println(network)
    }

    // @Test
    fun testDeleteNetwork(){
        val zeroTierClient = Client(authToken)
        val response = runBlocking { zeroTierClient.deleteNetwork("") }
        println(response)
    }

    // @Test
    fun testAddNetworkMember(){
        val zeroTierClient = Client(authToken)
        val memberConfig = MemberConfig.forCreate(authorized = true)
        val member = Member.forCreate("Test Name", memberConfig)
        val response = runBlocking { zeroTierClient.addNetworkMember("", "", member) }
        println(response)
    }

    // @Test
    fun testGetNetworkMembers(){
        val zeroTierClient = Client(authToken)
        val response = runBlocking { zeroTierClient.getNetworkMembers("") }
        println(response)
    }

    // @Test
    fun testGetNetworkMember() {
        val zeroTierClient = Client(authToken)
        val response = runBlocking { zeroTierClient.getNetworkMember("", "") }
        println(response)
    }

    // @Test
    fun testDeleteNetworkMember(){
        val zeroTierClient = Client(authToken)
        val response = runBlocking { zeroTierClient.deleteNetworkMember("", "") }
        println(response)
    }
}