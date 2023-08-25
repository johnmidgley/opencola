package opencola.core.network.providers.http

import io.opencola.model.Authority
import io.opencola.network.message.PingMessage
import io.opencola.network.message.PongMessage
import io.opencola.network.providers.http.HttpNetworkProvider
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
        val networkProvider = app.inject<HttpNetworkProvider>()
        val originalRequestHandler = networkProvider.getRequestHandler()!!

        try {
            runBlocking {
                val deferredResult = CompletableDeferred<String>()
                networkProvider.setMessageHandler { _, _, message ->
                    when (message) {
                         is PingMessage -> {
                            networkProvider.sendMessage(
                                persona,
                                persona,
                                PongMessage()
                            )
                        }

                        is PongMessage -> {
                            deferredResult.complete("Pong")
                        }

                        else -> throw IllegalArgumentException("Unknown message type: $message")
                    }
                }

                val goodMessage = PingMessage()
                networkProvider.sendMessage(persona, persona, goodMessage)
                withTimeout(3000) { deferredResult.await() }
            }

            val badKeyPair = generateKeyPair()
            val badAuthority = Authority(badKeyPair.public, persona.address, "Bad Authority")

            println("Testing sending message to unknown peer")
            assertFails {
                networkProvider.sendMessage(
                    persona,
                    AddressBookEntry(badAuthority),
                    PingMessage()
                )
            }

            println("Testing sending message from unknown persona")
            assertFails {
                val badPersonaAddressBookEntry = PersonaAddressBookEntry(badAuthority, badKeyPair)
                networkProvider.sendMessage(
                    badPersonaAddressBookEntry,
                    persona,
                    PingMessage()
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
        testConnectAndReplicate(getApplicationNode(), getApplicationNode())
    }
}