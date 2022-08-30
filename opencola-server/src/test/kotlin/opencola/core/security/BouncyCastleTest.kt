package opencola.core.security

import io.opencola.core.security.SecurityProviderDependent
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertNotNull


class BouncyCastleTest : SecurityProviderDependent() {
    @Test
    fun testUnrestrictedPolicy(){
        val cipher = Cipher.getInstance("Blowfish/ECB/NoPadding", "BC")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(ByteArray(32), "Blowfish")
        )

        // Will throw an exception if not available
    }

    @Test
    fun testProviders(){
        val bcProvider = Security.getProvider("BC")
        assertNotNull(bcProvider)
    }
}