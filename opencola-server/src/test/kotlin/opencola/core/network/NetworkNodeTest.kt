package opencola.core.network

import kotlinx.coroutines.runBlocking
import opencola.core.TestApplication
import opencola.core.network.zerotier.Client
import org.junit.Test
import org.kodein.di.instance
import kotlin.test.assertFalse

class NetworkNodeTest {
    // @Test
    fun testInvalidToken(){
        val networkNode by TestApplication.instance.injector.instance<NetworkNode>()
        assertFalse(networkNode.isNetworkTokenValid(""))
    }
}