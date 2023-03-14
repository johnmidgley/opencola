package opencola.core.network

import io.opencola.application.TestApplication
import io.opencola.network.InviteToken
import io.opencola.security.Signator
import opencola.server.handlers.getInviteToken
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.test.assertEquals

class InviteTokenTest {
    @Test
    fun testInviteToken() {
        val injector = TestApplication.instance.injector
        val persona = TestApplication.instance.getPersonas().first()
        val signator by injector.instance<Signator>()

        val inviteToken = InviteToken(persona.entityId, "Test Name", persona.publicKey, persona.address, URI("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTx5bRYo79dnn0X_9y11eFKD2GG6k3mOhb8fw&usqp=CAU"))
        val token = inviteToken.encodeBase58(signator)
        val inviteToken1 = InviteToken.decodeBase58(token)
        assertEquals(inviteToken, inviteToken1)
    }

    @Test
    fun testInviteTokenFromPeerHandler() {
        val app = TestApplication.instance
        val authority = TestApplication.instance.getPersonas().first()
        val inviteToken = InviteToken.decodeBase58(getInviteToken(authority.entityId, app.inject(), app.inject(), app.inject()))
        assertEquals(authority.publicKey, inviteToken.publicKey)
    }
}