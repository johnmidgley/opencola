package io.opencola.network

import io.opencola.model.Id
import io.opencola.network.message.SignedMessage
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import mu.KotlinLogging

class Route(val messageType: String, val handler: (Id, Id, SignedMessage) -> Unit)

// TODO: This should be wrapped by a RequestRouter that marks nodes as online upon successful request
// TODO: Rename requestHandler?
class RequestRouter(private val addressBook: AddressBook, private val routes: List<Route>) {
    private val logger = KotlinLogging.logger("RequestRouter")

    // TODO: Make response optional? When not present, should ignore.
    fun handleRequest(from: Id, to: Id, signedMessage: SignedMessage) {
        val peer = addressBook.getEntry(to, from)
            ?: throw IllegalArgumentException("Received request from unknown peer (from: $from to $to)")

        if(!peer.isActive)
            throw IllegalArgumentException("Received request from inactive peer (from: $from to: $to)")

        val persona = addressBook.getEntry(to, to) as? PersonaAddressBookEntry
            ?: throw IllegalArgumentException("Received request to invalid persona (from: $from to: $to)")

        if(!persona.isActive)
            throw IllegalArgumentException("Received request to inactive persona (from: $from to: $to)")


        val handler = routes.firstOrNull { it.messageType == signedMessage.body.type }?.handler
            ?: "No handler specified for ${signedMessage.body.type}".let {
                logger.error { it }
                return
            }

        return try {
            handler(from, to, signedMessage)
        } catch (e: Exception){
            logger.error { "Handler encountered error: $e" }
            throw e
        }
    }
}