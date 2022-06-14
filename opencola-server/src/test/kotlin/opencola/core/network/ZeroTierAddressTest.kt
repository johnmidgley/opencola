package opencola.core.network

import opencola.core.network.zerotier.ZeroTierAddress
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZeroTierAddressTest {
    val defaultRoot = URI("https://my.zerotier.com/api/v1")
    @Test
    fun testZeroTierAddress(){
        val address0 = ZeroTierAddress(null, null)
        assertNull(address0.networkId)
        assertNull(address0.nodeId)
        assertEquals(defaultRoot, address0.root)
        assertEquals(address0, ZeroTierAddress.fromURI(address0.toURI()))

        val networkId1 = "abc"
        val address1 = ZeroTierAddress(networkId1, null)
        assertEquals(networkId1, address1.networkId)
        assertNull(address1.nodeId)
        assertEquals(defaultRoot, address1.root)
        assertEquals(address1, ZeroTierAddress.fromURI(address1.toURI()))

        val networkId2 = "abc"
        val nodeId2 = "def"
        val address2 = ZeroTierAddress(networkId2, nodeId2)
        assertEquals(networkId2, address2.networkId)
        assertEquals(nodeId2, address2.nodeId)
        assertEquals(defaultRoot, address2.root)
        assertEquals(address2, ZeroTierAddress.fromURI(address2.toURI()))


        val networkId3 = "abc"
        val nodeId3 = "def"
        val root3 = URI("https://root")
        val address3 = ZeroTierAddress(networkId3, nodeId3, root3)
        assertEquals(networkId3, address3.networkId)
        assertEquals(nodeId3, address3.nodeId)
        assertEquals(root3, address3.root)
        assertEquals(address3, ZeroTierAddress.fromURI(address3.toURI()))

        val nodeId4 = "abc"
        val address4 = ZeroTierAddress(null, nodeId4)
        assertNull(address4.networkId)
        assertEquals(nodeId4, address4.nodeId)
        assertEquals(defaultRoot, address4.root)
        assertEquals(address4, ZeroTierAddress.fromURI(address4.toURI()))
    }
}