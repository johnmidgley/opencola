package opencola.server

import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import opencola.core.TestApplication
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.security.Encryptor
import opencola.core.security.generateKeyPair
import opencola.core.storage.AddressBook
import opencola.server.handlers.Peer
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureRouting
import org.junit.Test
import org.kodein.di.instance
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

class PeerHandlerTest {
    private val application = TestApplication.instance
    val injector = TestApplication.instance.injector

    @Test
    fun testSetNetworkToken(){
        val authority by injector.instance<Authority>()
        val addressBook by injector.instance<AddressBook>()
        val encryptor by injector.instance<Encryptor>()
        val networkToken = UUID.randomUUID().toString()
        val peer = Peer(authority.authorityId, authority.name!!, authority.publicKey!!, authority.uri!!, authority.imageUri, true, networkToken)

        withTestApplication({ configureRouting(application); configureContentNegotiation() }) {
            with(handleRequest(HttpMethod.Put, "/peers"){
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(Json.encodeToString(peer))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }

        val authority1 = addressBook.getAuthority(authority.authorityId)
        assertNotNull(authority1)
        assertNotNull(authority1.networkToken)
        val networkToken1 = String(encryptor.decrypt(authority.authorityId, authority1.networkToken!!))
        assertEquals(networkToken, networkToken1)
    }

    @Test
    fun testSetNetworkTokenForWrongAuthority(){
        val keyPair = generateKeyPair()
        val authority by injector.instance<Authority>()
        val networkToken = UUID.randomUUID().toString()
        val peer = Peer(Id.new(), authority.name!!, keyPair.public, authority.uri!!, authority.imageUri, true, networkToken)

        assertFails {
            withTestApplication({ configureRouting(application); configureContentNegotiation() }) {
                with(handleRequest(HttpMethod.Put, "/peers") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(Json.encodeToString(peer))
                }) {
                    assertEquals(HttpStatusCode.OK, response.status())
                }
            }
        }
    }
}