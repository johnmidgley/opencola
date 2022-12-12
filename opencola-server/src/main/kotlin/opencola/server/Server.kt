package opencola.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.opencola.core.config.Application
import io.opencola.core.config.Config
import io.opencola.core.config.LoginConfig
import io.opencola.core.config.ServerConfig
import io.opencola.core.event.EventBus
import io.opencola.core.event.Events
import io.opencola.core.model.Id
import io.opencola.core.network.NetworkNode
import io.opencola.core.security.encode
import io.opencola.core.security.getMd5Digest
import io.opencola.core.security.initProvider
import io.opencola.core.serialization.Base58
import io.opencola.core.system.detectResume
import io.opencola.core.system.openUri
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
import kotlin.concurrent.thread

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

    thread {
        runBlocking {
            launch {
                val eventBus = application.inject<EventBus>()
                detectResume { eventBus.sendMessage(Events.NodeResume.toString()) }
            }
        }
    }

    return application
}

fun onServerStarted(application: Application) {
    val hostAddress = Inet4Address.getLocalHost().hostAddress
    application.inject<NetworkNode>().start()
    application.inject<EventBus>().sendMessage(Events.NodeStarted.toString())
    logger.info { "Server started: http://$hostAddress:${application.config.server.port}" }
    application.config.server.ssl?.let {
        logger.info { "Server started: https://$hostAddress:${it.port} - certs needed" }
    }
}

@Serializable
data class ErrorResponse(val message: String)

fun getServer(application: Application, loginCredentials: LoginCredentials): NettyApplicationEngine {
    val serverConfig = application.config.server
    val realm = "/"
    val username = loginCredentials.username
    val userTable = mapOf(username to getMd5Digest("$username:$realm:${loginCredentials.password}"))
    val authToken = Base58.encode(userTable[username]!!)

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
            configureRouting(application)
            configureStatusPages()
            configureAuthentication()
            configureSessions()

            this.environment.monitor.subscribe(ApplicationStarted) { onServerStarted(application) }
        }
    }

    return embeddedServer(Netty, environment)
}

data class LoginCredentials(val username: String, val password: String)

suspend fun getLoginCredentials(storagePath: Path, serverConfig: ServerConfig, loginConfig: LoginConfig): LoginCredentials {
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
            configureBootstrapRouting(storagePath, serverConfig, loginConfig, loginCredentials)
            install(Sessions) {
                cookie<UserSession>("user_session")
            }
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
        launch {
            delay(1000)
            openUri(URI("http://localhost:${config.server.port}"))
        }

        val loginCredentials = getLoginCredentials(storagePath, config.server, config.security.login)
        val application = getApplication(storagePath, config, loginCredentials)
        getServer(application, loginCredentials).start()
    }
}