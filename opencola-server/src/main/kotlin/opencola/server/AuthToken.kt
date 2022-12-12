package opencola.server

import io.opencola.core.security.decrypt
import io.opencola.core.security.encrypt
import io.opencola.core.serialization.Base58
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

@Serializable
data class AuthToken(val username: String,
                     val lifespan: Long,
                     val issuedAt: Long,
                     val sessionId: String,
) {
    constructor(username: String, lifespan: Long) : this(username, lifespan, System.currentTimeMillis(), UUID.randomUUID().toString())

    fun isExpired(): Boolean {
        return System.currentTimeMillis() > issuedAt + lifespan
    }

    fun encode(publicKey: PublicKey): String {
        return Json.encodeToString(this)
            .toByteArray()
            .let { encrypt(publicKey, it) }
            .let { Base58.encode(it) }
    }

    companion object {
        fun decode(token: String, privateKey: PrivateKey): AuthToken? {
            return try {
                String(decrypt(privateKey, Base58.decode(token)))
                    .let { Json.decodeFromString(it) }
            } catch (_: Exception) {
                null
            }
        }
    }
}