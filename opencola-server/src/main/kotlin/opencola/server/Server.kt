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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import opencola.server.handlers.bootstrapInit
import opencola.server.plugins.configureBootstrapRouting
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
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
            configureRouting(application, authToken)
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val response = ErrorResponse(cause.message ?: "Unknown")
                    call.respond(HttpStatusCode.InternalServerError, Json.encodeToString(response))
                }
            }
            install(Authentication) {
                digest("auth-digest") {
                    this.realm = realm
                    digestProvider { userName, _ ->
                        userTable[userName]
                    }
                }
            }

            install(Sessions) {
                cookie<UserSession>("user_session")
            }

            this.environment.monitor.subscribe(ApplicationStarted) { onServerStarted(application) }
        }
    }

    return embeddedServer(Netty, environment)
}

data class LoginCredentials(val username: String, val password: String)
data class UserSession(val authToken: String)

suspend fun getLoginCredentials(storagePath: Path, serverConfig: ServerConfig, loginConfig: LoginConfig): LoginCredentials {
    val loginCredentials = CompletableDeferred<LoginCredentials>()

    if(loginConfig.password != null) {
        return LoginCredentials(loginConfig.username, loginConfig.password!!)
    }

    val environment = applicationEngineEnvironment {
        log = LoggerFactory.getLogger("ktor.application")

        // TODO: Without knowing the password for the cert file upfront, we can't connect ssl.
        //  1. The way it is know, you enter your password on an insecure form - but it's on your machine - problem?
        //  2. Could used a fixed password for cert store. Risks?
        //  3. Could pass credentials in to container - clunky if authentication fails or you want to change your password
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
            configureBootstrapRouting(storagePath, loginConfig, loginCredentials)
            install(Sessions) {
                cookie<UserSession>("user_session")
            }
        }
    }

    bootstrapInit(storagePath, serverConfig.ssl!!)
    val server = embeddedServer(Netty, environment).start(wait = false)
    val credentials = loginCredentials.await()
    server.stop(200,200)

    return credentials
}

fun startServer(storagePath: Path, config: Config, exitApplication: () -> Unit) {
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