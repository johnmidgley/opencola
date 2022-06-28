package opencola.core.network.zerotier

import java.time.Instant
import kotlin.test.assertNotNull

class ZeroTierClientTest {
    private val authToken = ""
    private val zeroTierClient = ZeroTierClient(authToken)

    private fun createNetwork() : Network {
        val epochSecond = Instant.now().epochSecond
        val networkConfig = NetworkConfig.forCreate(
            name = "Test Network: $epochSecond ",
            private = true,
            routes = listOf(Route("172.27.0.0/16")),
            // v4AssignMode = IPV4AssignMode(true),
            // ipAssignmentPools = listOf(IPRange("10.243.0.1", "10.243.255.254"))
        )
        val network = Network.forCreate(networkConfig, "Test Description")
        val zeroTierClient = ZeroTierClient(authToken)
        return zeroTierClient.createNetwork(network)
    }

    private fun addNetworkMember(networkId: String, memberId: String) : Member {
        val memberConfig = MemberConfig.forCreate(authorized = true)
        val member = Member.forCreate("Test Name", memberConfig)
        return zeroTierClient.addNetworkMember(networkId, memberId, member)
    }

    // @Test
    fun testZeroTierClient() {
        val network = createNetwork()
        val networkId = network.id!!
        assertNotNull(networkId)
        // Can't compare network from getNetwork with one from getNetworks - clock param changes for each call.
        zeroTierClient.getNetwork(networkId)
        zeroTierClient.getNetworks().single { it.id == networkId }
        val memberId = "0ff4ab2f61"
        addNetworkMember(networkId, memberId)
        zeroTierClient.getNetworkMember(networkId, memberId)
        zeroTierClient.getNetworkMembers(networkId).single { it.nodeId == memberId }
        zeroTierClient.deleteNetworkMember(networkId, memberId)
        zeroTierClient.getNetworkMembers(networkId).none { it.nodeId == memberId }
        zeroTierClient.deleteNetwork(networkId)
        zeroTierClient.getNetworks().none { it.id == networkId }

    }
}