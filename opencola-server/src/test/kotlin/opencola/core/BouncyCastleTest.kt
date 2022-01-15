package opencola.core

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test


class BouncyCastleTest {


    @Test
    fun testUnrestrictedPolicy(){
        Security.addProvider(BouncyCastleProvider())

        val cipher = Cipher.getInstance("Blowfish/ECB/NoPadding", "BC")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(ByteArray(32), "Blowfish")
        )

        // Will throw an exception if not available
    }

    @Test
    fun testProviders(){
        Security.addProvider(BouncyCastleProvider())
        val bcProvider = Security.getProvider("BC")

        bcProvider.keys.map { it as String }.filter { it.contains("EC") }.forEach{ println(it)}
    }
}