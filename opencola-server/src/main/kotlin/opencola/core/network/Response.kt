package opencola.core.network

import kotlinx.serialization.Serializable

@Serializable
data class Response(val status: Int, val message: String?, val body: ByteArray?)