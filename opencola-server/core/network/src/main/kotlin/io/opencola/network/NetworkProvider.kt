package io.opencola.network

import io.opencola.model.Id
import io.opencola.security.*
import io.opencola.storage.AddressBook
import io.opencola.security.Encryptor
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.PersonaAddressBookEntry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

interface NetworkProvider {
    fun start(waitUntilReady: Boolean = false)
    fun stop()

    fun getScheme() : String
    fun validateAddress(address: URI)

    // If a peer URI changes with the same provider, it will result in removePeer(oldPeer) addPeer(newPeer)
    fun addPeer(peer: AddressBookEntry)
    fun removePeer(peer: AddressBookEntry)

    fun sendRequest(from: PersonaAddressBookEntry, to: AddressBookEntry, request: Request): Response?
    fun setRequestHandler(handler: (Id, Id, Request) -> Response)
}

// TODO: just pass in keystore instead of signator and encryptor? Or maybe even just role into AddressBook?
abstract class AbstractNetworkProvider(val addressBook: AddressBook,
                                       val signator: Signator,
                                       val encryptor: Encryptor,
) : NetworkProvider {
    var handler: ((Id, Id, Request) -> Response)? = null
    var started = false

    override fun setRequestHandler(handler: (Id, Id, Request) -> Response) {
        this.handler = handler
    }

    fun getEncodedEnvelope(fromId: Id, toId: Id, messageBytes: ByteArray, encryptMessage: Boolean): ByteArray {
        val toAuthority = addressBook.getEntry(fromId, toId)
            ?: throw IllegalArgumentException("$fromId does not have $toId as peer")
        val message = Message(fromId, messageBytes, signator.signBytes(fromId.toString(), messageBytes).bytes)
        return MessageEnvelope(toId, message).encode(if (encryptMessage) toAuthority.publicKey else null)
    }

    fun validateMessageEnvelope(messageEnvelope: MessageEnvelope) {
        val message = messageEnvelope.message

        // TODO: Change all if / throw IllegalArgumentException to require
        if(addressBook.getEntry(messageEnvelope.to, messageEnvelope.to) !is PersonaAddressBookEntry) {
            throw IllegalArgumentException("Received message for non local authority: ${messageEnvelope.to}")
        }

        val fromPersona = addressBook.getEntry(messageEnvelope.to, message.from)
            ?: throw IllegalArgumentException("Message is from unknown peer: ${message.from}")

        if(!isValidSignature(fromPersona.publicKey, message.body, message.signature)) {
            throw IllegalArgumentException("Received message from $fromPersona with invalid signature")
        }
    }

    fun handleMessage(envelopeBytes: ByteArray, useEncryption: Boolean) : ByteArray {
        if (!started) throw IllegalStateException("Provider is not started - can't handleRequest")
        val handler = this.handler ?: throw IllegalStateException("Call to handleRequest when handler has not been set")
        val encryptor = if (useEncryption) this.encryptor else null
        val envelope = MessageEnvelope.decode(envelopeBytes, encryptor).also { validateMessageEnvelope(it) }
        val response =  handler(envelope.message.from, envelope.to, Json.decodeFromString(String(envelope.message.body)))

        return getEncodedEnvelope(envelope.to, envelope.message.from, Json.encodeToString(response).toByteArray(), useEncryption)
    }
}