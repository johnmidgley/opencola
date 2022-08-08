package opencola.core.network.providers.http

import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.network.Request
import opencola.server.PeerTest
import opencola.core.network.RequestRouter
import opencola.core.network.Response
import org.junit.Test
import kotlin.test.assertNotNull

class HttpNetworkProviderTest : PeerTest(){
    @Test
    fun testHttpNetworkProvider() {
        val applicationNode = getApplicationNode(0, zeroTierIntegrationEnabled = false, persistent = false).also { it.start() }

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