package io.opencola.network.providers

import io.opencola.model.Id
import io.opencola.network.message.Message
import io.opencola.security.Signator
import io.opencola.storage.addressbook.AddressBook

// TODO: just pass in keystore instead of signator and encryptor? Or maybe even just role into AddressBook?
abstract class AbstractNetworkProvider(
    val addressBook: AddressBook,
    val signator: Signator,
) : NetworkProvider {
    private var eventHandler: EventHandler? = null
    private var messageHandler: MessageHandler? = null
    var started = false

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

    abstract fun handleMessage(envelopeBytes: ByteArray, context: ProviderContext? = null)

    // TODO: Should from / to be ids or address book entries?
    fun handleMessage(from: Id, to: Id, message: Message) {
        messageHandler?.invoke(from, to, message) ?: throw IllegalStateException("No message handler set")
    }
}