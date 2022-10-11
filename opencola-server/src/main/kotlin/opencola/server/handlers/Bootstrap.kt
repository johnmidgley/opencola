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

suspend fun bootstrapForm(call: ApplicationCall, username: String, message: String? = null) {
    call.respondHtml {
        body {
            form(action = "/", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                if(message != null) {
                    p {
                        +message
                    }
                }
                p {
                    +"Username:"
                    textInput {
                        name = "username"
                        value = username
                    }
                }
                p {
                    +"Password:"
                    passwordInput(name = "password")
                }
                p {
                    submitInput { value = "Start" }
                }
            }
            p {
                a {
                    href = "/changePassword"
                    +"Change Password"
                }
            }
        }
    }
}

suspend fun bootstrapChangePasswordForm(call: ApplicationCall, isNewUser: Boolean, message: String? = null, error: String? = null) {
    call.respondHtml {
        body {
            form(action = "/changePassword", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                if(isNewUser) {
                    p {
                        +"Welcome to OpenCola! Please set a password."
                    }
                }
                if(message != null) {
                    p {
                        +message
                    }
                }
                if(!isNewUser) {
                    p {
                        +"Old Password:"
                        passwordInput(name = "oldPassword")
                    }
                }
                p {
                    +"Password:"
                    passwordInput(name = "password")
                }
                p {
                    +"Confirm:"
                    passwordInput(name = "passwordConfirm")
                }
                p {
                    submitInput() { value = "Change Password" }
                }
            }
        }
    }
}

suspend fun startingPage(call: ApplicationCall) {
    call.respondHtml {
        body {
            +"OpenCola is starting..."
            script {
                unsafe {
                    raw("""
                        setTimeout("window.location = '/';",5000);
                        """)
                }
            }
        }
    }
}

// TODO: Clean this up - probably can abstract cert store better and move most of these functions there
fun validateCertKeyStorePassword(storagePath: Path, password: String): Boolean {
    try {
        val certKeyStoreFile = getCertStorePath(storagePath).toFile()
        if (certKeyStoreFile.exists()) {
            val keyStore = KeyStore.getInstance("PKCS12", "BC")
            certKeyStoreFile.inputStream().use { keyStore.load(certKeyStoreFile.inputStream(), password.toCharArray()) }
        }
    } catch (e: Exception) {
        logger.error { "Bad cert password: ${e.message}" }
        return false
    }

    return true
}

fun getCertStorePath(storagePath: Path) : Path {
    return storagePath.resolve("cert/opencola-ssl.pks")
}

fun getAuthorityStorePath(storagePath: Path): Path {
    return storagePath.resolve("keystore.pks")
}

// TODO: Move to cert store specific place
fun changeSSLCertStorePassword(storagePath: Path, oldPassword: String, newPassword: String) {
    val certKeyStorePath = getCertStorePath(storagePath)

    if(certKeyStorePath.exists())
        changePassword(certKeyStorePath, oldPassword, newPassword)
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

fun validatePassword(storagePath: Path, password: String) : Boolean {
    val certStorePasswordValid = validateCertKeyStorePassword(storagePath, password)
    val authorityStorePasswordValid = validateAuthorityKeyStorePassword(storagePath, password)

    if(certStorePasswordValid xor authorityStorePasswordValid) {
        logger.error { "Cert and Authority keystore passwords do not match. Delete your cert store so that it will be regenerated." }
    }

    return certStorePasswordValid && authorityStorePasswordValid
}

fun passwordExists(storagePath: Path) : Boolean {
    return getCertStorePath(storagePath).exists() || getAuthorityStorePath(storagePath).exists()
}

fun changePasswords(storagePath: Path, oldPassword: String, newPassword: String) {
    changeSSLCertStorePassword(storagePath, oldPassword, newPassword)
    changeAuthorityKeyStorePassword(storagePath, oldPassword, newPassword)
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
