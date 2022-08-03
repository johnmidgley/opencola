package opencola.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class Response(val status: Int, val message: String? = null, val body: ByteArray? = null)

inline fun <reified T> response(status: Int, message: String? = null, body: T) : Response {
    return Response(status, message, Json.encodeToString(body).toByteArray())
}