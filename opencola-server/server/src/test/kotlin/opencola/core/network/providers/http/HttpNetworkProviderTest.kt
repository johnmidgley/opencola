package opencola.core.network.providers.http

import io.opencola.model.Authority
import io.opencola.core.network.Request
import io.opencola.core.network.providers.http.HttpNetworkProvider
import io.opencola.security.generateKeyPair
import opencola.server.PeerNetworkTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HttpNetworkProviderTest : PeerNetworkTest() {
    @Test
    fun testHttpNetworkProvider() {
        val applicationNode =
            getApplicationNode().also { it.start() }

        try {
            val app = applicationNode.application
            val authority = app.inject<Authority>()
            val networkProvider = app.inject<HttpNetworkProvider>()

            val goodRequest = Request(Request.Method.GET, "/ping", null, null)
            val response = networkProvider.sendRequest(authority, authority, goodRequest)
            assertNotNull(response)
            assertEquals("pong", response.message)

            val badKeyPair = generateKeyPair()
            val badAuthority = Authority(badKeyPair.public, authority.uri!!, "Bad Authority")

            val badRequest = Request(Request.Method.GET, "/ping", null, null)

            // TODO: These should probably throw, rather than return null
            assertNull(networkProvider.sendRequest(app.inject(), badAuthority, badRequest))
            assertNull(networkProvider.sendRequest(badAuthority, app.inject(), badRequest))
        } finally {
            applicationNode.stop()
        }
    }

    // TODO: Add test that makes request without signature, bad signature, bad public key, inactive authority

    @Test
    fun testHttpConnectAndReplicate() {
        val application0 = getApplicationNode().also { it.start() }
        val application1 = getApplicationNode().also { it.start() }

        try {
            testConnectAndReplicate(application0, application1)
        } finally {
            application0.stop()
            application1.stop()
        }
    }
}