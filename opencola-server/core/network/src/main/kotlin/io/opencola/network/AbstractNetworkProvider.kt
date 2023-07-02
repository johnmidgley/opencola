package io.opencola.network

import io.opencola.model.Id
import io.opencola.network.message.UnsignedMessage
import io.opencola.network.message.MessageEnvelope
import io.opencola.network.message.SignedMessage
import io.opencola.security.Encryptor
import io.opencola.security.Signator
import io.opencola.security.isValidSignature
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry

// TODO: just pass in keystore instead of signator and encryptor? Or maybe even just role into AddressBook?
abstract class AbstractNetworkProvider(
    val addressBook: AddressBook,
    val signator: Signator,
    val encryptor: Encryptor,
) : NetworkProvider {
    private var eventHandler: EventHandler? = null
    private var messageHandler: MessageHandler? = null
    var started = false

    fun getPersonasForProvider() : List<PersonaAddressBookEntry> {
        return addressBook.getEntries()
            .filterIsInstance<PersonaAddressBookEntry>()
            .filter { it.address.scheme == getScheme() }
    }

    override fun setEventHandler(handler: EventHandler) {
        this.eventHandler = handler
    }

    fun handleEvent(event: ProviderEvent) {
        eventHandler?.invoke(event) ?: throw IllegalStateException("No event handler set")
    }

    override fun setMessageHandler(handler: MessageHandler) {
        this.messageHandler = handler
    }

    // TODO: Remove this and create a new handler for testing
    fun getRequestHandler() = this.messageHandler

    fun getEncodedEnvelope(fromId: Id, toId: Id, signedMessage: SignedMessage, encryptMessage: Boolean): ByteArray {
        addressBook.getEntry(fromId, fromId) ?: throw IllegalArgumentException("FromId $fromId is not a persona")
        addressBook.getEntry(fromId, toId) ?: throw IllegalArgumentException("$fromId does not have $toId as peer")
        return MessageEnvelope(toId, signedMessage).encode(if (encryptMessage) addressBook else null)
    }

    // TODO: This should be removed. Signing should be done centrally by the NetworkNode
    fun getEncodedEnvelope(fromId: Id, toId: Id, message: UnsignedMessage, encryptMessage: Boolean): ByteArray {
        return getEncodedEnvelope(
            fromId,
            toId,
            SignedMessage(fromId, message, signator.signBytes(fromId.toString(), message.payload)),
            encryptMessage
        )
    }

    private fun validateMessageEnvelope(messageEnvelope: MessageEnvelope) {
        val signedMessage = messageEnvelope.signedMessage

        // TODO: Change all if / throw IllegalArgumentException to require
        if (addressBook.getEntry(messageEnvelope.to, messageEnvelope.to) !is PersonaAddressBookEntry) {
            throw IllegalArgumentException("Received message for non local authority: ${messageEnvelope.to}")
        }

        val fromPersona = addressBook.getEntry(messageEnvelope.to, signedMessage.from)
            ?: throw IllegalArgumentException("Message is from unknown peer: ${signedMessage.from}")

        if (!isValidSignature(fromPersona.publicKey, signedMessage.body.payload, signedMessage.signature)) {
            throw IllegalArgumentException("Received message from $fromPersona with invalid signature")
        }
    }

    fun handleMessage(envelopeBytes: ByteArray, useEncryption: Boolean) {
        if (!started) throw IllegalStateException("Provider is not started - can't handleRequest")
        val handler = messageHandler ?: throw IllegalStateException("Call to handleRequest when handler has not been set")
        val encryptor = if (useEncryption) this.encryptor else null
        val envelope = MessageEnvelope.decode(envelopeBytes, encryptor).also { validateMessageEnvelope(it) }
        handler(envelope.signedMessage.from, envelope.to, envelope.signedMessage)
    }
}