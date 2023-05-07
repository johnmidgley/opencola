package opencola.core.network.providers.http

import io.opencola.network.providers.http.HttpNetworkProvider
import opencola.server.PeerNetworkTest
import org.junit.Test

class HttpNetworkProviderTest : PeerNetworkTest() {
    @Test
    fun testHttpNetworkProvider() {
        val applicationNode =
            getApplicationNode().also { it.start() }

        try {
            val app = applicationNode.application
            val persona = app.getPersonas().first()
            val networkProvider = app.inject<HttpNetworkProvider>()

//            val goodMessage = PingMessage()
//            val response = networkProvider.sendMessage(persona, persona, goodMessage)
//            assertNotNull(response)
//            assertEquals("pong", response.message)
//
//            val badKeyPair = generateKeyPair()
//            val badAuthority = Authority(badKeyPair.public, persona.address, "Bad Authority")
//
//            val badRequest = Request(Request.Method.GET, "/ping", null, null)
//
//            // TODO: These should probably throw, rather than return null
//            assertNull(networkProvider.sendMessage(persona, AddressBookEntry(badAuthority), badRequest))
//            assertNull(networkProvider.sendMessage(PersonaAddressBookEntry(badAuthority, badKeyPair), persona, badRequest))
            TODO("Fix this test")
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