package opencola.server

import io.opencola.core.security.encrypt
import io.opencola.core.security.generateKeyPair
import io.opencola.core.security.initProvider
import io.opencola.core.serialization.Base58
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthTokenTest {
    private val keyPair = generateKeyPair()

    init {
        initProvider()
    }

    @Test
    fun testValidAuthToken() {
        val authToken = AuthToken("test", 1000)
        val encoded = authToken.encode(keyPair.public)
        val decoded = AuthToken.decode(encoded, keyPair.private)

        assert(decoded != null)
        assertEquals(authToken, decoded)
        assert(!decoded!!.isExpired())
    }

    @Test
    fun testExpiredToken() {
        val authToken = AuthToken("test", 1000, System.currentTimeMillis() - 2000, UUID.randomUUID().toString())
        val encoded = authToken.encode(keyPair.public)
        val decoded = AuthToken.decode(encoded, keyPair.private)

        assert(decoded != null)
        assertEquals(authToken, decoded)
        assert(decoded!!.isExpired())
    }

    @Test
    fun testInvalidToken() {
        val encoded = encrypt(keyPair.public, "invalid".toByteArray()).let { Base58.encode(it) }
        val decoded = AuthToken.decode(encoded, keyPair.private)
        assert(decoded == null)
    }
}