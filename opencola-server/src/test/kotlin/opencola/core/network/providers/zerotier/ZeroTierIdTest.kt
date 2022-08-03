package opencola.core.network.providers.zerotier

import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals

class ZeroTierIdTest {
    @Test
    fun testZeroTierId() {
        val nodeId = "7222cdc894"
        val zeroTierNodeId = ZeroTierId(nodeId)

        assertEquals(nodeId, zeroTierNodeId.toString())
        assertEquals(nodeId, ZeroTierId(zeroTierNodeId.toLong()).toString())
        assertEquals(nodeId, ZeroTierId(BigInteger(nodeId, 16)).toString())

        val networkId = "d3ecf5726d5f15f6"
        val zeroTierNetworkId = ZeroTierId(networkId)

        val l = zeroTierNetworkId.toLong().toString(16)
        assertEquals(networkId, zeroTierNetworkId.toString())
        assertEquals(networkId, ZeroTierId(zeroTierNetworkId.toLong()).toString())
        assertEquals(networkId, ZeroTierId(BigInteger(networkId, 16)).toString())
    }
}