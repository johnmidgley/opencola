package opencola.core.network

import opencola.core.TestApplication
import opencola.core.model.Authority
import opencola.core.security.Signator
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.test.assertEquals

val testToken = "113BFtDGzbx2N4bx89Q17QRWDYfDpY7uKiW4TjWF4qJBrTsV1A5aoU9gMFtFPBcsiom3fzhgfqGND7JJm1LhFPyMQELM9jWdYKinvxFojBGS6fGTKHAhyodz3RM4BjjBxYzeJGbZ8D7ZVQcg5JRQU7M9c9infsL6kgAheLtHH8CgZtkSKcLrMQPyu9TwPyZcKA7LMFitRQS4jBVmpBwtffUEN6yF1PK1aBe8jrFDRCvwJhtfggpiG6ciJTcJPaqZhJQ7J9X36Pr7w3e6bGtbtecFirAFZ28vMNtaiS4AC2hkv4kbEKxPucGiawtmJqTK3X389aXJJtuhEkiTy2y3aQJCGDH3bJD8FKWEtpsLm21rMjvtA3eTomYPoWv4SSTS4SG3diWrfBipoQAZfCDAXug4ELadUnf84byVyExiowx3yfae72pfCrGw37bA5pYSnjE2VLqBy4spcMAAigkcnrGqFzy7Una1i56LVWdgXuC9xcYsCeYwAbmZ38n67ST3nqDTUj4KhvUxAo9vW7"

class InviteTokenTest {
    @Test
    fun testInviteToken() {
        val injector = TestApplication.instance.injector
        val authority by injector.instance<Authority>()
        val signator by injector.instance<Signator>()

        val inviteToken = InviteToken(authority.entityId, "Test Name", authority.publicKey!!, authority.uri!!, URI("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTx5bRYo79dnn0X_9y11eFKD2GG6k3mOhb8fw&usqp=CAU"))
        val token = inviteToken.encodeBase58(signator)
        val inviteToken1 = InviteToken.decodeBase58(token)
        assertEquals(inviteToken, inviteToken1)
    }

    @Test
    fun testInviteTokenFromNetworkNode() {
        val networkNode = TestApplication.instance.inject<NetworkNode>()
        val authority = TestApplication.instance.inject<Authority>()

        val token = networkNode.getInviteToken()
        assertEquals(authority.publicKey, token.publicKey)
    }
}