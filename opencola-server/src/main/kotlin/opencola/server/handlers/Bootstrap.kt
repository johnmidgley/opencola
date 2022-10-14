package opencola.server.handlers

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.opencola.core.config.Application
import io.opencola.core.config.SSLConfig
import io.opencola.core.security.changePassword
import kotlinx.html.*
import mu.KotlinLogging
import opencola.server.getSSLCertificateStore
import java.nio.file.Path
import java.security.KeyStore
import kotlin.io.path.exists
import io.opencola.core.security.KeyStore as OpenColaKeyStore

private val logger = KotlinLogging.logger("Bootstrap")

fun getCertStorePath(storagePath: Path) : Path {
    return storagePath.resolve("cert/opencola-ssl.pks")
}

fun getAuthorityStorePath(storagePath: Path): Path {
    return storagePath.resolve("keystore.pks")
}

fun validateAuthorityKeyStorePassword(storagePath: Path, password: String): Boolean {
    try {
        val keyStorePath = getAuthorityStorePath(storagePath)
        if(keyStorePath.exists()) {
            OpenColaKeyStore(keyStorePath, password)
        }
    } catch (e: Exception) {
        if(password != "password")
            logger.error { "Bad keystore password: ${e.message }" }
        return false
    }

    return true
}

// TODO: Move to authority store specific place
fun changeAuthorityKeyStorePassword(storagePath: Path, oldPassword: String, newPassword: String) {
    OpenColaKeyStore(getAuthorityStorePath(storagePath), oldPassword).changePassword(newPassword)
}

fun isNewUser(storagePath: Path): Boolean {
    val authorityStorePath = getAuthorityStorePath(storagePath)
    return !authorityStorePath.exists() || validateAuthorityKeyStorePassword(storagePath, "password")
}

fun bootstrapInit(storagePath: Path, sslConfig: SSLConfig) {
    val authorityStorePath = getAuthorityStorePath(storagePath)
    val certStorePath = getCertStorePath(storagePath)

    if(isNewUser(storagePath)) {
        if(!authorityStorePath.exists())
            Application.getOrCreateRootKeyPair(storagePath, "password")

        if(!certStorePath.exists())
            getSSLCertificateStore(storagePath, "password", sslConfig)
    }
}
