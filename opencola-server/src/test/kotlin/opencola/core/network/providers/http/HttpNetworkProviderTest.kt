package opencola.core.network.providers.http

import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import io.opencola.core.network.Request
import io.opencola.core.network.providers.http.HttpNetworkProvider
import opencola.server.PeerTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HttpNetworkProviderTest : PeerTest() {
    @Test
    fun testHttpNetworkProvider() {
        val applicationNode =
            getApplicationNode(0, zeroTierIntegrationEnabled = false, persistent = false).also { it.start() }

        try {
            val app = applicationNode.application
            val networkProvider = app.inject<HttpNetworkProvider>()

            val goodRequest = Request(app.inject<Authority>().entityId, Request.Method.GET, "/ping", null, null)
            val response = networkProvider.sendRequest(app.inject(), goodRequest)
            assertNotNull(response)
            assertEquals("pong", response.message)

            val badRequest = Request(Id.new(), Request.Method.GET, "/ping", null, null)
            assertNull(networkProvider.sendRequest(app.inject(), badRequest))
        } finally {
            applicationNode.stop()
        }
    }

    // TODO: Add test that makes request without signature, bad signature, bad public key, inactive authority
}