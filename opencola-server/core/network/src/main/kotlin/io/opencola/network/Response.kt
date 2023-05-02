package io.opencola.network

import kotlinx.serialization.Serializable

@Serializable
data class Response(
    val status: Int,
    val message: String? = null,
    val headers: Map<String, String>? = null,
    val body: ByteArray? = null
) {
    override fun toString(): String {
        return "Response(status=$status, message=$message, headers=$headers, body.size=${body?.size})"
    }
}