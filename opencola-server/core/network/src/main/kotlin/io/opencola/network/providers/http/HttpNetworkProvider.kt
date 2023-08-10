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
import io.opencola.network.NoPendingMessagesEvent
import io.opencola.network.message.SignedMessage
import io.opencola.security.*
import io.opencola.storage.addressbook.AddressBook
import io.opencola.security.Encryptor
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import java.net.URI

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

        // There are no queued / buffered messages over http, so we can let the network know that all peers
        // are ready for transaction requests
        getPersonasForProvider().forEach { persona -> handleEvent(NoPendingMessagesEvent(persona.entityId)) }
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
        require (started) { "Provider is not started - can't sendMessage" }

        try {
            val urlString = "${to.address}/networkNode"
            logger.info { "Sending request $signedMessage" }

            return runBlocking {
                val httpResponse = httpClient.post(urlString) {
                    contentType(ContentType.Application.OctetStream)
                    signedMessage.encode()
                    val encryptedPayload =
                        getEncodedEnvelope(from.entityId, to.entityId, signedMessage, true)
                    setBody(encryptedPayload)
                }

                if (httpResponse.status != HttpStatusCode.OK)
                    throw RuntimeException("Peer request resulted in error $httpResponse: ${httpResponse.body<String>()}")
            }
        } catch (e: java.net.ConnectException) {
            logger.info { "${to.name} appears to be offline." }
        } catch (e: Exception) {
            logger.error { "sendRequest: $e" }
            throw e
        }
    }

    // Network Node validates the call
    override fun sendMessage(from: PersonaAddressBookEntry, to: Set<AddressBookEntry>, signedMessage: SignedMessage) {
        to.forEach { peer ->
            sendMessage(from, peer, signedMessage)
        }
    }
}