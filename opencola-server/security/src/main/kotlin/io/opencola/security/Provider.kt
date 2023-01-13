package io.opencola.security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

fun initProvider(){
    Security.addProvider(BouncyCastleProvider())
}

// Deriving from this will automatically init the provider
abstract class SecurityProviderDependent {
    companion object Initializer {
        init{
            initProvider()
        }
    }
}
