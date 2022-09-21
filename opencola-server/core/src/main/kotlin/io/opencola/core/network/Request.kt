package io.opencola.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.opencola.core.extensions.nullOrElse
import io.opencola.core.model.Id

@Serializable
// TODO: Should include encoding parameter - headers?
data class Request(
    val from: Id,
    val method: Method,
    val path: String,
    val headers: Map<String, String>? = null,
    val parameters: Map<String, String>? = null,
    val body: ByteArray? = null,
) {
    override fun toString(): String {
        return "Request(from=$from, method=$method, path=$path, headers=$headers, parameters=$parameters, body.size=${body?.size})"
    }
    enum class Method {
        DELETE,
        GET,
        HEAD,
        OPTIONS,
        PATCH,
        POST,
        PUT,
    }

    inline fun <reified T> decodeBody(): T? {
        return body.nullOrElse { Json.decodeFromString<T>(String(it)) }
    }
}

inline fun <reified T> request(from: Id,
                               method: Request.Method,
                               path: String,
                               headers: Map<String, String>? = null,
                               parameters: Map<String, String>? = null,
                               body: T) : Request {
    return Request(from, method, path, headers, parameters, Json.encodeToString(body).toByteArray())
}