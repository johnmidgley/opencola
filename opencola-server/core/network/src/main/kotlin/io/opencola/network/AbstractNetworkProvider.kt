package io.opencola.network

import io.opencola.model.Id
import io.opencola.network.message.UnsignedMessage
import io.opencola.network.message.MessageEnvelope
import io.opencola.network.message.SignedMessage
import io.opencola.security.Encryptor
import io.opencola.security.Signator
import io.opencola.security.isValidSignature
import io.opencola.storage.AddressBook
import io.opencola.storage.PersonaAddressBookEntry

// TODO: just pass in keystore instead of signator and encryptor? Or maybe even just role into AddressBook?
abstract class AbstractNetworkProvider(val addressBook: AddressBook,
                                       val signator: Signator,
                                       val encryptor: Encryptor,
) : NetworkProvider {
    var handler: ((Id, Id, SignedMessage) -> Unit)? = null
    var started = false

    override fun setRequestHandler(handler: (Id, Id, SignedMessage) -> Unit) {
        this.handler = handler
    }

    fun getEncodedEnvelope(fromId: Id, toId: Id, message: UnsignedMessage, encryptMessage: Boolean): ByteArray {
        val toAuthority = addressBook.getEntry(fromId, toId)
            ?: throw IllegalArgumentException("$fromId does not have $toId as peer")
        val signedMessage = SignedMessage(fromId, message, signator.signBytes(fromId.toString(), message.payload))
        return MessageEnvelope(toId, signedMessage).encode(if (encryptMessage) toAuthority.publicKey else null)
    }

    fun validateMessageEnvelope(messageEnvelope: MessageEnvelope) {
        val signedMessage = messageEnvelope.signedMessage

        // TODO: Change all if / throw IllegalArgumentException to require
        if(addressBook.getEntry(messageEnvelope.to, messageEnvelope.to) !is PersonaAddressBookEntry) {
            throw IllegalArgumentException("Received message for non local authority: ${messageEnvelope.to}")
        }

        val fromPersona = addressBook.getEntry(messageEnvelope.to, signedMessage.from)
            ?: throw IllegalArgumentException("Message is from unknown peer: ${signedMessage.from}")

        if(!isValidSignature(fromPersona.publicKey, signedMessage.message.payload, signedMessage.signature)) {
            throw IllegalArgumentException("Received message from $fromPersona with invalid signature")
        }
    }

    fun handleMessage(envelopeBytes: ByteArray, useEncryption: Boolean) {
        if (!started) throw IllegalStateException("Provider is not started - can't handleRequest")
        val handler = this.handler ?: throw IllegalStateException("Call to handleRequest when handler has not been set")
        val encryptor = if (useEncryption) this.encryptor else null
        val envelope = MessageEnvelope.decode(envelopeBytes, encryptor).also { validateMessageEnvelope(it) }
        handler(envelope.signedMessage.from, envelope.to, envelope.signedMessage)
    }
}