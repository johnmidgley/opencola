package opencola.server.handlers

import io.opencola.core.network.Request
import io.opencola.core.network.Response
import io.opencola.core.network.providers.http.HttpNetworkProvider
import io.opencola.core.security.isValidSignature
import io.opencola.core.storage.AddressBook
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

suspend fun handleNetworkNode(
    addressBook: AddressBook,
    httpNetworkProvider: HttpNetworkProvider,
    payload: ByteArray,
    signature: ByteArray?
): Response {
    signature ?: throw IllegalArgumentException("Missing signature")

    val json = String(payload)
    val request = Json.decodeFromString<Request>(json)
    val fromAuthority =
        addressBook.getAuthority(request.from) ?: throw IllegalArgumentException("Unknown authority: ${request.from}")

    val fromPublicKey = fromAuthority.publicKey
        ?: throw IllegalStateException("Request from peer without public key: ${request.from}")

    // TODO: Check if authority is active
    if (!isValidSignature(fromPublicKey, payload, signature))
        throw RuntimeException("Request signature not valid")

    // TODO: Sign response?
    return httpNetworkProvider.handleRequest(request)
}