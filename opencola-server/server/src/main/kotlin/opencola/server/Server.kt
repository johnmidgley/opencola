package opencola.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.opencola.core.config.Application
import io.opencola.core.config.Config
import io.opencola.core.config.LoginConfig
import io.opencola.core.config.ServerConfig
import io.opencola.core.event.EventBus
import io.opencola.core.event.Events
import io.opencola.core.model.Id
import io.opencola.core.network.NetworkNode
import io.opencola.core.security.EncryptionParams
import io.opencola.core.security.encode
import io.opencola.core.security.initProvider
import io.opencola.core.system.detectResume
import io.opencola.core.system.openUri
import io.opencola.core.system.runningInDocker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.server.handlers.bootstrapInit
import opencola.server.plugins.*
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.URI
import java.nio.file.Path

private val logger = KotlinLogging.logger("opencola")
private const val sslCertStorePassword = "password"
private val provider = initProvider()

fun getApplication(
    storagePath: Path,
    config: Config,
    loginCredentials: LoginCredentials
): Application {
    // TODO: Is getOrCreateRootKeyPair needed outside of App.instance()?
    val keyPair = Application.getOrCreateRootKeyPair(storagePath, loginCredentials.password)
    val application = Application.instance(storagePath, config, keyPair, loginCredentials.password)
    application.logger.info("Authority: ${Id.ofPublicKey(keyPair.public)}")
    application.logger.info("Public Key : ${keyPair.public.encode()}")

    return application
}

fun onServerStarted(application: Application) {
    val hostAddress = Inet4Address.getLocalHost().hostAddress
    application.inject<NetworkNode>().start()
    application.inject<EventBus>().let {
        it.sendMessage(Events.NodeStarted.toString())
        detectResume { it.sendMessage(Events.NodeResume.toString()) }
    }

    logger.info { "Server started: http://$hostAddress:${application.config.server.port}" }
    application.config.server.ssl?.let {
        logger.info { "Server started: https://$hostAddress:${it.port} - certs needed" }
    }
}

@Serializable
data class ErrorResponse(val message: String)

fun getServer(application: Application, authEncryptionParams: EncryptionParams): NettyApplicationEngine {
    val serverConfig = application.config.server

    val environment = applicationEngineEnvironment {
        log = LoggerFactory.getLogger("ktor.application")

        connector {
            host = serverConfig.host
            port = serverConfig.port
        }

        if (serverConfig.ssl != null) {
            sslConnector(
                keyStore = getSSLCertificateStore(application.storagePath, sslCertStorePassword, serverConfig.ssl!!),
                keyAlias = "opencola-ssl",
                keyStorePassword = { sslCertStorePassword.toCharArray() },
                privateKeyPassword = { sslCertStorePassword.toCharArray() }
            ) {
                host = serverConfig.host
                port = serverConfig.ssl!!.port
            }
        }

        module {
            configureHTTP()
            configureContentNegotiation()
            configureAuthentication(application.config.security.login, authEncryptionParams)
            configureRouting(application, authEncryptionParams)
            configureStatusPages()
            configureSessions()

            this.environment.monitor.subscribe(ApplicationStarted) { onServerStarted(application) }
        }
    }

    return embeddedServer(Netty, environment)
}

data class LoginCredentials(val username: String, val password: String)

suspend fun getLoginCredentials(
    storagePath: Path,
    serverConfig: ServerConfig,
    loginConfig: LoginConfig,
    authEncryptionParams: EncryptionParams,
): LoginCredentials {
    val loginCredentials = CompletableDeferred<LoginCredentials>()

    if(loginConfig.password != null) {
        return LoginCredentials(loginConfig.username, loginConfig.password!!)
    }

    val environment = applicationEngineEnvironment {
        log = LoggerFactory.getLogger("ktor.application")

        connector {
            host = serverConfig.host
            port = serverConfig.port
        }

        if (serverConfig.ssl != null) {
            sslConnector(
                keyStore = getSSLCertificateStore(storagePath, sslCertStorePassword, serverConfig.ssl!!),
                keyAlias = "opencola-ssl",
                keyStorePassword = { sslCertStorePassword.toCharArray() },
                privateKeyPassword = { sslCertStorePassword.toCharArray() }
            ) {
                host = serverConfig.host
                port = serverConfig.ssl!!.port
            }
        }

        module {
            configureHTTP()
            configureBootstrapRouting(storagePath, serverConfig, loginConfig, authEncryptionParams, loginCredentials)
            configureSessions()
        }
    }

    bootstrapInit(storagePath, serverConfig.ssl!!)
    val server = embeddedServer(Netty, environment).start(wait = false)
    val credentials = loginCredentials.await()
    server.stop(500,500)

    return credentials
}

fun startServer(storagePath: Path, config: Config) {
    runBlocking {
        if (!runningInDocker()) {
            launch {
                delay(1000)
                openUri(URI("http://localhost:${config.server.port}"))
            }
        }

        val loginCredentials = getLoginCredentials(storagePath, config.server, config.security.login, AuthToken.encryptionParams)
        val application = getApplication(storagePath, config, loginCredentials)
        getServer(application, AuthToken.encryptionParams).start()
    }
}