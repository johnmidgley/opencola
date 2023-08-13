package opencola.server

import io.opencola.security.*
import io.opencola.util.Base58
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.*
import javax.crypto.SecretKey

@Serializable
data class AuthToken(
    val username: String,
    val lifespan: Long,
    val issuedAt: Long,
    val sessionId: String,
) {
    constructor(
        username: String,
        tokenLifespan : Long = 1000 * 60 * 60 * 24 * 365L, // 1 year
    ) : this(username, tokenLifespan, System.currentTimeMillis(), UUID.randomUUID().toString())

    fun isValid(): Boolean {
        return System.currentTimeMillis() <= issuedAt + lifespan
    }

    fun encode(secretKey: SecretKey): String {
        return Json.encodeToString(this)
            .toByteArray()
            .let { encrypt(secretKey, it) }
            .let { Base58.encode(it.encodeProto()) }
    }

    companion object {

        fun decode(secretKey: SecretKey, token: String): AuthToken? {
            return try {
                String(decrypt(secretKey, EncryptedBytes.decodeProto(Base58.decode(token))))
                    .let { Json.decodeFromString(it) }
            } catch (_: Exception) {
                null
            }
        }
    }
}