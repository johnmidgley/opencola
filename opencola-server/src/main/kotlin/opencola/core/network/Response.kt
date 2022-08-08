package opencola.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import opencola.core.extensions.nullOrElse

@Serializable
data class Response(val status: Int, val message: String? = null, val headers: Map<String, String>? = null, val body: ByteArray? = null){
    inline fun <reified T> decodeBody() : T? {
        return body.nullOrElse { Json.decodeFromString<T>(String(it)) }
    }
}

inline fun <reified T> response(status: Int, message: String? = null, headers: Map<String, String>? = null,body: T) : Response {
    return Response(status, message, headers, Json.encodeToString(body).toByteArray())
}