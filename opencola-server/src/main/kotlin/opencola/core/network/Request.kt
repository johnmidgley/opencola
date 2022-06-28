package opencola.core.network

import kotlinx.serialization.Serializable
import opencola.core.model.Id

@Serializable
data class Request(val from: Id, val method: String, val path: String, val body: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Request

        if (from != other.from) return false
        if (method != other.method) return false
        if (path != other.path) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}