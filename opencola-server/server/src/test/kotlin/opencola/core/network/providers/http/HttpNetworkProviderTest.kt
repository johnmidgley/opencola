package opencola.core.network.providers.http

import io.opencola.model.Authority
import io.opencola.network.NetworkNode
import io.opencola.network.message.PingMessage
import io.opencola.network.message.PongMessage
import io.opencola.network.message.SignedMessage
import io.opencola.network.providers.http.HttpNetworkProvider
import io.opencola.security.Signature
import io.opencola.security.generateKeyPair
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import opencola.server.getApplicationNode
import opencola.server.testConnectAndReplicate
import org.junit.Test
import kotlin.test.assertFails

class HttpNetworkProviderTest {
    @Test
    fun testHttpNetworkProvider() {
        val applicationNode =
            getApplicationNode().also { it.start() }
        val app = applicationNode.application
        val persona = app.getPersonas().first()
        val networkNode = app.inject<NetworkNode>()
        val networkProvider = app.inject<HttpNetworkProvider>()
        val originalRequestHandler = networkProvider.getRequestHandler()!!

        try {
            runBlocking {
                val deferredResult = CompletableDeferred<String>()
                networkProvider.setMessageHandler { fromId, toId, signedMessage ->
                    val message = signedMessage.body
                    when (message.type) {
                        PingMessage.messageType -> {
                            networkNode.signMessage(persona, PongMessage())
                            networkProvider.sendMessage(
                                persona,
                                persona,
                                networkNode.signMessage(persona, PongMessage())
                            )
                        }

                        PongMessage.messageType -> {
                            deferredResult.complete("Pong")
                        }

                        else -> throw IllegalArgumentException("Unknown message type: ${message.type}")
                    }
                }

                val goodMessage = networkNode.signMessage(persona, PingMessage())
                networkProvider.sendMessage(persona, persona, goodMessage)
                withTimeout(3000) { deferredResult.await() }
            }

            val badKeyPair = generateKeyPair()
            val badAuthority = Authority(badKeyPair.public, persona.address, "Bad Authority")

            assertFails {
                networkProvider.sendMessage(
                    persona,
                    AddressBookEntry(badAuthority),
                    SignedMessage(
                        persona.personaId,
                        PingMessage(),
                        Signature("", io.opencola.network.emptyByteArray)
                    )
                )
            }

            assertFails {
                val badPersonaAddressBookEntry = PersonaAddressBookEntry(badAuthority, badKeyPair)
                networkProvider.sendMessage(
                    badPersonaAddressBookEntry,
                    persona,
                    SignedMessage(
                        badPersonaAddressBookEntry.personaId,
                        PingMessage(),
                        Signature("", io.opencola.network.emptyByteArray)
                    )
                )
            }
        } finally {
            applicationNode.stop()
            networkProvider.setMessageHandler(originalRequestHandler)
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