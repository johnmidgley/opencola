package opencola.server

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.opencola.core.config.Application
import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import io.opencola.core.network.Message
import io.opencola.core.network.MessageEnvelope
import io.opencola.core.network.Request
import io.opencola.core.network.Response
import io.opencola.core.network.providers.relay.OCRelayNetworkProvider
import io.opencola.core.security.decodePublicKey
import io.opencola.core.security.decrypt
import io.opencola.core.security.sign
import io.opencola.core.serialization.IntByteArrayCodec
import io.opencola.relay.common.WebSocketSessionWrapper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.net.URI
import java.nio.file.Path
import java.security.KeyPair
import java.util.*

class RelayTransactionsTest {
    private val logger = KotlinLogging.logger("RelayTransactionsTest")

    suspend fun connectToRelay(keyPair: KeyPair): WebSocketSessionWrapper {
        val client = HttpClient(CIO) {
            install(WebSockets) {
                // Configure WebSockets
                pingInterval = 1000 * 55 // TODO: Make configurable
            }
        }

        return WebSocketSessionWrapper(client.webSocketSession(HttpMethod.Get, "relay.opencola.net", 2652, "/relay"))
    }

    suspend fun authenticate(socketSession: WebSocketSessionWrapper, keyPair: KeyPair) {
        // Send public key
        socketSession.writeSizedByteArray(keyPair.public.encoded)

        // Read challenge
        val challengeBytes = socketSession.readSizedByteArray()

        // Sign challenge and send back
        socketSession.writeSizedByteArray(sign(keyPair.private, challengeBytes))

        val authenticationResponse = IntByteArrayCodec.decode(socketSession.readSizedByteArray())
        if (authenticationResponse != 0) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResponse")
        }

        logger.info { "Authenticated" }
    }

    fun peerAuthority(): Authority {
        val publicKey = decodePublicKey("aSq9DsNNvGhYxYyqA9wd2eduEAZ5AXWgJTbTG35yC3f373W7ALePkMxxrg5fu5VfKXx82FK5Um6dUtLQGxW5ETmbBdUL6CsY26jatDGsczraUaPYKwKoVq9i2haq")
        return Authority(publicKey, URI(""), "Mini")
    }
    fun requestTransactions(storagePath: Path) {
        runBlocking {
            // Open Session
            val keyPair = Application.getOrCreateRootKeyPair(storagePath, "password")
            val authorityId = Id.ofPublicKey(keyPair.public)
            val session = connectToRelay(keyPair).also { authenticate(it, keyPair) }

            // Create peer
            val peerPublicKey = decodePublicKey("aSq9DsNNvGhYxYyqA9wd2eduEAZ5AXWgJTbTG35yC3f373W7ALePkMxxrg5fu5VfKXx82FK5Um6dUtLQGxW5ETmbBdUL6CsY26jatDGsczraUaPYKwKoVq9i2haq")
            val peer = Authority(peerPublicKey, URI(""), "Mini")

            // Create Request
            val params = mapOf("authorityId" to peer.entityId.toString())
            val request = Request(Request.Method.GET, "/transactions", null, params)

            // Create Envelope
            val messageBytes = Json.encodeToString(request).toByteArray()
            val networkMessage = Message(authorityId, messageBytes, sign(keyPair.private, messageBytes))
            val networkEnvelope = MessageEnvelope(peer.entityId, networkMessage).encode( null)
            val relayMessage = io.opencola.relay.common.Message(keyPair, UUID.randomUUID(), networkEnvelope)
            val relayEnvelope = io.opencola.relay.common.MessageEnvelope(peerPublicKey, relayMessage)

            logger.info { "Sending message" }
            val sendTime = System.currentTimeMillis()
            session.writeSizedByteArray(io.opencola.relay.common.MessageEnvelope.encode(relayEnvelope))
            logger.info { "Reading response" }

            val result = session.readSizedByteArray()

            val receiveTime = System.currentTimeMillis()
            val elapsedTime = receiveTime - sendTime

            logger.info { "Time elapsed = ${elapsedTime / 1000 }" }

            val responseRelayEnvelop = io.opencola.relay.common.MessageEnvelope.decode(result)
            val responseRelayMessage = io.opencola.relay.common.Message.decode(decrypt(keyPair.private, responseRelayEnvelop.message))
            val responseNetworkEnvelope = MessageEnvelope.decode(responseRelayMessage.body)
            val responseNetworkMessage = responseNetworkEnvelope.message
            val response = Json.decodeFromString<Response>(String(responseNetworkMessage.body))

            logger.info { response }

            logger.info { "Done" }
        }
    }

    fun getTransactionsNetworkProvider(app: Application) {
        val peerPublicKey = decodePublicKey("aSq9DsNNvGhYxYyqA9wd2eduEAZ5AXWgJTbTG35yC3f373W7ALePkMxxrg5fu5VfKXx82FK5Um6dUtLQGxW5ETmbBdUL6CsY26jatDGsczraUaPYKwKoVq9i2haq")
        val peer = Authority(peerPublicKey, URI("ocr://relay.opencola.net"), "Mini")

        // Create Request
        val params = mapOf("authorityId" to peer.entityId.toString())
        val request = Request(Request.Method.GET, "/transactions", null, params)

        val networkProvider = app.inject<OCRelayNetworkProvider>()
        val response = networkProvider.sendRequest(app.inject(), peer, request)

        print("Done")
    }
}