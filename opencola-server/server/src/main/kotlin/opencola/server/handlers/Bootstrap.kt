package opencola.server.handlers

import io.opencola.application.Application
import io.opencola.application.SSLConfig
import io.opencola.security.hash.Hash
import io.opencola.security.keystore.JavaKeyStore
import io.opencola.security.keystore.defaultPasswordHash
import mu.KotlinLogging
import opencola.server.getSSLCertificateStore
import java.nio.file.Path
import kotlin.io.path.exists

private val logger = KotlinLogging.logger("bootstrap")

// TODO: These look like they could live elsewhere

fun getCertStorePath(storagePath: Path) : Path {
    return storagePath.resolve("cert/opencola-ssl.pks")
}

fun getAuthorityStorePath(storagePath: Path): Path {
    return storagePath.resolve("keystore.pks")
}

fun validateAuthorityKeyStorePassword(storagePath: Path, passwordHash: Hash): Boolean {
    try {
        val keyStorePath = getAuthorityStorePath(storagePath)
        if(keyStorePath.exists()) {
            JavaKeyStore(keyStorePath, passwordHash)
        }
    } catch (e: Exception) {
        if(passwordHash != defaultPasswordHash)
            logger.error { "Bad keystore password: ${e.message }" }
        return false
    }

    return true
}

// TODO: Move to authority store specific place
fun changeAuthorityKeyStorePassword(storagePath: Path, oldPasswordHash: Hash, newPasswordHash: Hash) {
    JavaKeyStore(getAuthorityStorePath(storagePath), oldPasswordHash).changePassword(newPasswordHash)
}

fun isNewUser(storagePath: Path): Boolean {
    val authorityStorePath = getAuthorityStorePath(storagePath)
    return !authorityStorePath.exists() || validateAuthorityKeyStorePassword(storagePath, defaultPasswordHash)
}

fun bootstrapInit(storagePath: Path, sslConfig: SSLConfig) {
    val authorityStorePath = getAuthorityStorePath(storagePath)
    val certStorePath = getCertStorePath(storagePath)

    if(isNewUser(storagePath)) {
        if(!authorityStorePath.exists())
            Application.getOrCreateRootKeyPair(storagePath, defaultPasswordHash)

        if(!certStorePath.exists())
            getSSLCertificateStore(storagePath, "password", sslConfig)
    }
}
