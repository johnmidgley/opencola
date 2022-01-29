package opencola.core.security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

fun initProvider(){
    Security.addProvider(BouncyCastleProvider())
}

abstract class SecurityProviderDependent {
    companion object Initializer {
        init{
            initProvider()
        }
    }
}
