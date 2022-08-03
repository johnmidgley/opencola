package opencola.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import opencola.core.model.Id

@Serializable
// TODO: Should include encoding parameter - headers?
data class Request(val from: Id, val method: Method, val path: String, val params: Map<String, String>?, val body: ByteArray) {
    enum class Method {
        DELETE,
        GET,
        HEAD,
        OPTIONS,
        PATCH,
        POST,
        PUT,
    }

    inline fun <reified T> decodeBody() : T {
        return Json.decodeFromString<T>(String(body))
    }
}