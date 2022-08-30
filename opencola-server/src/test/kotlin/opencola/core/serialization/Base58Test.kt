package opencola.core.serialization

import io.opencola.core.model.Id
import io.opencola.core.security.generateKeyPair
import io.opencola.core.serialization.Base58
import org.junit.Test
import kotlin.test.assertContentEquals

class Base58Test {
    @Test
    fun testEncodeIdBase58(){
        val idBytes = Id.encode(Id.new())
        val decodedBytes = Base58.decode(Base58.encode(idBytes))

        assertContentEquals(idBytes, decodedBytes)
    }

    @Test
    fun testEncodePublicKeyBase58(){
        val publicKeyBytes = generateKeyPair().public.encoded
        val decodedBytes = Base58.decode(Base58.encode(publicKeyBytes))

        assertContentEquals(publicKeyBytes, decodedBytes)
    }
}