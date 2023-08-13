package opencola.server

import io.opencola.security.encrypt
import io.opencola.security.generateAesKey
import io.opencola.security.generateKeyPair
import io.opencola.security.initProvider
import io.opencola.util.Base58
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
        val secretKey = generateAesKey()
        val encoded = authToken.encode(secretKey)
        val decoded = AuthToken.decode(secretKey, encoded)

        assert(decoded != null)
        assertEquals(authToken, decoded)
        assert(decoded!!.isValid())
    }

    @Test
    fun testExpiredToken() {
        val authToken = AuthToken("test", 1000, System.currentTimeMillis() - 2000, UUID.randomUUID().toString())
        val secretKey = generateAesKey()
        val encoded = authToken.encode(secretKey)
        val decoded = AuthToken.decode(secretKey, encoded)

        assert(decoded != null)
        assertEquals(authToken, decoded)
        assert(!decoded!!.isValid())
    }

    @Test
    fun testInvalidToken() {
        val secretKey = generateAesKey()
        val encoded = encrypt(keyPair.public, "invalid".toByteArray()).let { Base58.encode(it.bytes) }
        val decoded = AuthToken.decode(secretKey, encoded)
        assert(decoded == null)
    }
}