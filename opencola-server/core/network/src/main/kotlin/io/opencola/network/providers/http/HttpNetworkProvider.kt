package io.opencola.network.providers.http

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.opencola.network.NetworkConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import io.opencola.model.Authority
import io.opencola.network.AbstractNetworkProvider
import io.opencola.network.*
import io.opencola.network.MessageEnvelope
import io.opencola.network.Response
import io.opencola.security.*
import io.opencola.storage.AddressBook
import io.opencola.security.Encryptor
import kotlinx.serialization.encodeToString
import java.net.URI
import kotlin.IllegalStateException

class HttpNetworkProvider(
    addressBook: AddressBook,
    signator: Signator,
    encryptor: Encryptor,
    networkConfig: NetworkConfig,
) : AbstractNetworkProvider(addressBook, signator, encryptor) {
    private val logger = KotlinLogging.logger("HttpNetworkProvider")

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            if (networkConfig.socksProxy != null) {
                logger.info { "Using Socks Proxy: ${networkConfig.socksProxy}" }
                proxy = ProxyBuilder.socks(networkConfig.socksProxy.host, networkConfig.socksProxy.port)
            }
        }
    }

    override fun start(waitUntilReady: Boolean) {
        started = true
        logger.info { "Started" }
    }

    override fun stop() {
        started = false
        logger.info { "Stopped" }
    }

    override fun getScheme(): String {
        return "http"
    }

    override fun validateAddress(address: URI) {
        // Just needs to be a valid URI
        return
    }

    override fun addPeer(peer: Authority) {
        // Nothing to do
    }

    override fun removePeer(peer: Authority) {
        // Nothing to do
    }

    // Caller (Network Node) should check if peer is active
    override fun sendRequest(from: Authority, to: Authority, request: Request): Response? {
        if (!started) throw IllegalStateException("Provider is not started - can't sendRequest")

        to.publicKey
            ?: throw IllegalArgumentException("Can't send a request to a peer that does not have a public key")

        // TODO: Make sure to authority is actually remote (not local authority)

        try {
            val urlString = "${to.uri}/networkNode"
            logger.info { "Sending request $request" }

            return runBlocking {
                val httpResponse = httpClient.post(urlString) {
                    contentType(ContentType.Application.OctetStream)
                    val encryptedPayload =
                        getEncodedEnvelope(from.entityId, to.entityId, Json.encodeToString(request).toByteArray(), true)
                    setBody(encryptedPayload)
                }

                if (httpResponse.status != HttpStatusCode.OK)
                    throw RuntimeException("Peer request resulted in error $httpResponse: ${httpResponse.body<String>()}")

                val envelope =
                    MessageEnvelope.decode(httpResponse.body(), encryptor).also { validateMessageEnvelope(it) }
                val response = Json.decodeFromString<Response>(String(envelope.message.body))
                logger.info { "Response: $response" }
                response
            }
        } catch (e: java.net.ConnectException) {
            logger.info { "${to.name} appears to be offline." }
        } catch (e: Exception) {
            logger.error { "sendRequest: $e" }
        }

        return null
    }
}