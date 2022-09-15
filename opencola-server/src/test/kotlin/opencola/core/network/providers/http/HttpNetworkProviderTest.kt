package opencola.core.network.providers.http

import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import io.opencola.core.network.Request
import io.opencola.core.network.RequestRouter
import io.opencola.core.network.Response
import io.opencola.core.network.providers.http.HttpNetworkProvider
import opencola.server.PeerTest
import org.junit.Test
import kotlin.test.assertNotNull

class HttpNetworkProviderTest : PeerTest() {
    @Test
    fun testHttpNetworkProvider() {
        val applicationNode =
            getApplicationNode(0, zeroTierIntegrationEnabled = false, persistent = false).also { it.start() }

        try {
            val app = applicationNode.application
            val authority = app.inject<Authority>()
            val requestRouter = app.inject<RequestRouter>()
            val handler: (Request) -> Response = { request -> requestRouter.handleRequest(request) }
            // TODO: Bad - this is mutating the state of the app. Should go through network node.
            val httpNetworkProvider = app.inject<HttpNetworkProvider>().also { it.setRequestHandler(handler) }
            val request = Request(Id.new(), Request.Method.GET, "/ping", null, null)
            val response = httpNetworkProvider.sendRequest(authority, request)
            assertNotNull(response)
        } finally {
            applicationNode.stop()
        }
    }
}