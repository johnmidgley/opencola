package opencola.server

import io.opencola.core.security.*
import io.opencola.util.Base58
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.*

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

    fun encode(encryptionParams: EncryptionParams): String {
        return Json.encodeToString(this)
            .toByteArray()
            .let { encrypt(encryptionParams, it) }
            .let { Base58.encode(it) }
    }

    companion object {
        val encryptionParams = EncryptionParams("AES/CBC/PKCS5Padding", generateAesKey(), generateIv())

        fun decode(encryptionParams: EncryptionParams, token: String): AuthToken? {
            return try {
                String(decrypt(encryptionParams, Base58.decode(token)))
                    .let { Json.decodeFromString(it) }
            } catch (_: Exception) {
                null
            }
        }
    }
}