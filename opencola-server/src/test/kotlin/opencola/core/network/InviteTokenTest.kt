package opencola.core.network

import opencola.core.TestApplication
import opencola.core.model.Authority
import opencola.core.security.Signator
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.test.assertEquals

class InviteTokenTest {
    @Test
    fun testInviteToken() {
        val injector = TestApplication.instance.injector
        val authority by injector.instance<Authority>()
        val signator by injector.instance<Signator>()

        val inviteToken = InviteToken(authority.entityId, "Test Name", authority.publicKey!!, authority.uri!!, URI("https://Test"))
        val token = inviteToken.encodeBase58(signator)
        val inviteToken1 = InviteToken.decodeBase58(token)
        assertEquals(inviteToken, inviteToken1)
    }
}