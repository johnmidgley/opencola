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
import mu.KotlinLogging
import io.opencola.network.AbstractNetworkProvider
import io.opencola.network.message.SignedMessage
import io.opencola.security.*
import io.opencola.storage.AddressBook
import io.opencola.security.Encryptor
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.PersonaAddressBookEntry
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

    override fun addPeer(peer: AddressBookEntry) {
        // Nothing to do
    }

    override fun removePeer(peer: AddressBookEntry) {
        // Nothing to do
    }

    // Caller (Network Node) should check if peer is active
    override fun sendMessage(from: PersonaAddressBookEntry, to: AddressBookEntry, signedMessage: SignedMessage) {
        if (!started) throw IllegalStateException("Provider is not started - can't sendRequest")

        // TODO: Make sure to authority is actually remote (not local authority)

        try {
            val urlString = "${to.address}/networkNode"
            logger.info { "Sending request $signedMessage" }

            return runBlocking {
                val httpResponse = httpClient.post(urlString) {
                    contentType(ContentType.Application.OctetStream)
                    TODO("Encode body")
//                    val encryptedPayload =
//                        getEncodedEnvelope(from.entityId, to.entityId, Json.encodeToString(signedMessage).toByteArray(), true)
//                    setBody(encryptedPayload)
                }

                if (httpResponse.status != HttpStatusCode.OK)
                    throw RuntimeException("Peer request resulted in error $httpResponse: ${httpResponse.body<String>()}")
            }
        } catch (e: java.net.ConnectException) {
            logger.info { "${to.name} appears to be offline." }
        } catch (e: Exception) {
            logger.error { "sendRequest: $e" }
        }
    }
}