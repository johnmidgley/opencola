package opencola.core.network

import kotlinx.serialization.Serializable
import opencola.core.model.Id

@Serializable
data class Request(val from: Id, val method: Method, val path: String, val body: ByteArray) {
    enum class Method {
        DELETE,
        GET,
        HEAD,
        OPTIONS,
        PATCH,
        POST,
        PUT,
    }
}