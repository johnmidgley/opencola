package opencola.server.handlers

import io.opencola.core.config.Application
import io.opencola.core.config.SSLConfig
import mu.KotlinLogging
import opencola.server.getSSLCertificateStore
import java.nio.file.Path
import kotlin.io.path.exists
import io.opencola.core.security.KeyStore as OpenColaKeyStore

private val logger = KotlinLogging.logger("bootstrap")

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

private fun certInstalledIndicatorPath(storagePath: Path): Path {
    return getCertStorePath(storagePath)
        .toString()
        .substringBeforeLast(".")
        .plus(".installed")
        .let { Path.of(it) }
}

fun isCertInstalled(storagePath: Path): Boolean {
    return certInstalledIndicatorPath(storagePath).exists()
}

fun setCertInstalled(storagePath: Path) {
    certInstalledIndicatorPath(storagePath).toFile().createNewFile()
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
