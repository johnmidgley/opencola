/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.network.providers.http

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.opencola.model.Id
import io.opencola.network.NetworkConfig
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import io.opencola.network.providers.AbstractNetworkProvider
import io.opencola.network.NoPendingMessagesEvent
import io.opencola.network.message.*
import io.opencola.network.providers.ProviderContext
import io.opencola.security.*
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import java.net.URI
import java.security.PublicKey

class HttpNetworkProvider(
    addressBook: AddressBook,
    signator: Signator, // TODO: Likely don't need signator anymore - keyPair in persona entry
    networkConfig: NetworkConfig,
) : AbstractNetworkProvider(addressBook, signator) {
    private val logger = KotlinLogging.logger("HttpNetworkProvider")

    private val providerKeyPair = generateKeyPair()
    val publicKey: PublicKey
        get() = providerKeyPair.public


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
        addressBook.getPeers()
            .filter { it.address.scheme == getScheme() }
            .forEach { handleEvent(NoPendingMessagesEvent(it.personaId, it.address)) }
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

    private fun getPayload(
        from: PersonaAddressBookEntry,
        to: AddressBookEntry,
        headerPublicKey: PublicKey,
        message: Message
    ): ByteArray {
        val messageSecretKey = generateAesKey()

        // TODO: Break this up (decode payload too)
        val recipient = Recipient(from, to, messageSecretKey)
        val header = EnvelopeHeader(recipient, message.messageStorageKey)
        val encryptedHeader = encrypt(headerPublicKey, header.encodeProto())
        val signedHeader = sign(from.keyPair.private, encryptedHeader.encodeProto())

        val messagePayload = MessagePayload(from.entityId, message).encodeProto()
        val encryptedMessage = encrypt(messageSecretKey, messagePayload)
        val signedMessage = sign(from.keyPair.private, encryptedMessage.encodeProto())

        val envelope = Envelope(signedHeader, signedMessage)
        return envelope.encodeProto()
    }

    private fun getPersona(id: Id): PersonaAddressBookEntry {
        return addressBook.getEntry(id, id)?.let { it as PersonaAddressBookEntry }
            ?: throw RuntimeException("No persona found for id $id")
    }

    private fun getPeer(personaId: Id, peerId: Id): AddressBookEntry {
        return addressBook.getEntry(personaId, peerId)
            ?: throw RuntimeException("No peer found for persona=$personaId peer=$peerId")
    }

    private class DecodedPayload(
        val from: AddressBookEntry,
        val to: PersonaAddressBookEntry,
        val message: Message,
    )

    private fun decodePayload(payload: ByteArray): DecodedPayload {
        val envelope = Envelope.decodeProto(payload)
        val header = envelope.header.bytes
            .let { EncryptedBytes.decodeProto(it) }
            .let { decrypt(providerKeyPair.private, it) }
            .let { EnvelopeHeader.decodeProto(it) }
        require(header.recipients.size == 1) { "HttpNetworkProvider only supports one recipient" }
        val recipient = header.recipients.single()
        val toPersona = getPersona(recipient.to)
        val messageSecretKey = recipient.decryptMessageSecretKey(toPersona.keyPair.private)
        val messagePayload = envelope.message.bytes
            .let { EncryptedBytes.decodeProto(it) }
            .let { decrypt(messageSecretKey, it) }
            .let { MessagePayload.decodeProto(it) }
        val peer = getPeer(toPersona.entityId, messagePayload.from)
        require(envelope.header.validate(peer.publicKey)) { "Invalid header signature" }
        require(recipient.messageSecretKey.validate(peer.publicKey)) { "Invalid messageSecretKey signature" }
        messagePayload.message

        return DecodedPayload(peer, toPersona, messagePayload.message)
    }

    private suspend fun getServerPublicKey(networkNodeUrlStirng: String): PublicKey {
        return publicKeyFromBytes(httpClient.get("$networkNodeUrlStirng/pk").body<ByteArray>())
    }

    // Caller (Network Node) should check if peer is active
    // TODO: Should this be sendMessage(to: Id, message: Message) with to: Id in the message?
    override fun sendMessage(from: PersonaAddressBookEntry, to: AddressBookEntry, message: Message) {
        require(started) { "Provider is not started - can't sendMessage" }

        try {
            val urlString = "${to.address}/networkNode"
            logger.info { "Sending request $message" }

            return runBlocking {
                val serverPublicKey = getServerPublicKey(urlString)

                logger.info { "Sending request" }
                val httpResponse = httpClient.post(urlString) {
                    contentType(ContentType.Application.OctetStream)
                    setBody(getPayload(from, to, serverPublicKey, message))
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
    override fun sendMessage(from: PersonaAddressBookEntry, to: Set<AddressBookEntry>, message: Message) {
        to.forEach { peer ->
            sendMessage(from, peer, message)
        }
    }

    override fun handleMessage(envelopeBytes: ByteArray, context: ProviderContext?) {
        val decodedPayload = decodePayload(envelopeBytes)
        handleMessage(decodedPayload.from.entityId, decodedPayload.to.entityId, decodedPayload.message)
    }
}