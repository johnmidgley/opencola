package io.opencola.network

import io.opencola.model.Id
import io.opencola.storage.AddressBook
import io.opencola.storage.PersonaAddressBookEntry
import mu.KotlinLogging

class Route(val method: Request.Method, val path: String, val handler: (Id, Id, Request) -> Response)

// TODO: This should be wrapped by a RequestRouter that marks nodes as online upon successful request
// TODO: Rename requestHandler?
class RequestRouter(private val addressBook: AddressBook, private val routes: List<Route>) {
    private val logger = KotlinLogging.logger("RequestRouter")

    // TODO: Make response optional? When not present, should ignore.
    fun handleRequest(from: Id, to: Id, request: Request) : Response {
        val peer = addressBook.getEntry(to, from)
            ?: throw IllegalArgumentException("Received request from unknown peer (from: $from to $to)")

        if(!peer.isActive)
            throw IllegalArgumentException("Received request from inactive peer (from: $from to: $to)")

        val persona = addressBook.getEntry(to, to) as? PersonaAddressBookEntry
            ?: throw IllegalArgumentException("Received request to invalid persona (from: $from to: $to)")

        if(!persona.isActive)
            throw IllegalArgumentException("Received request to inactive persona (from: $from to: $to)")


        val handler = routes.firstOrNull { it.method == request.method && it.path == request.path }?.handler
            ?: "No handler specified for ${request.method} ${request.path}".let {
                logger.error { it }
                return Response(404, it, null)
            }

        return try{
            handler(from, to, request)
        } catch (e: Exception){
            logger.error { "Handler encountered error: $e" }
            // TODO: What should be exposed to caller?
            Response(500, e.toString(), null)
        }
    }
}