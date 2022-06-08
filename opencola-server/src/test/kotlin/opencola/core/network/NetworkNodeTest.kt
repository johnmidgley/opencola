package opencola.core.network

import opencola.core.TestApplication
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