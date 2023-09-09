package opencola.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.opencola.application.Application
import io.opencola.application.Config
import io.opencola.application.LoginConfig
import io.opencola.application.ServerConfig
import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.network.NetworkNode
import io.opencola.security.encode
import io.opencola.security.generateAesKey
import io.opencola.security.hash.Hash
import io.opencola.security.hash.Sha256Hash
import io.opencola.security.initProvider
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.system.detectResume
import io.opencola.system.openUri
import io.opencola.system.runningInDocker
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
import javax.crypto.SecretKey
import kotlin.text.toCharArray

private val logger = KotlinLogging.logger("opencola")
private const val sslCertStorePassword = "password"
private val provider = initProvider()

fun getApplication(
    storagePath: Path,
    config: Config,
    loginCredentials: LoginCredentials
): Application {
    // TODO: Is getOrCreateRootKeyPair needed outside of App.instance()?
    val hashedPassword = loginCredentials.passwordHash
    val keyPairs = Application.getOrCreateRootKeyPair(storagePath, hashedPassword)
    val application = Application.instance(storagePath, config, keyPairs, hashedPassword)
    val addressBook = application.inject<AddressBook>()

    addressBook.getEntries()
        .filterIsInstance<PersonaAddressBookEntry>()
        .forEach {
            application.logger.info("Persona: name = ${it.name},  id =${it.entityId}, publicKey = ${it.keyPair.public.encode()}")
        }

    return application
}

fun onServerStarted(application: Application) {
    try {
        val hostAddress = Inet4Address.getLocalHost().hostAddress
        val eventBus = application.inject<EventBus>().also { it.start() }
        application.inject<NetworkNode>().start()
        eventBus.sendMessage(Events.NodeStarted.toString())
        application.config.system.resume.let {
            if (it.enabled) {
                logger.info { "Resume detection enabled" }
                detectResume(
                    it.desiredDelayMillis,
                    it.maxDelayMillis
                ) { eventBus.sendMessage(Events.NodeResume.toString()) }
            } else
                logger.info { "Resume detection disabled" }
        }

        logger.info { "Server started: http://$hostAddress:${application.config.server.port}" }
        application.config.server.ssl?.let {
            logger.info { "Server started: https://$hostAddress:${it.port} - certs needed" }
        }
    } catch (e: Throwable) {
        logger.error { "Error starting server: $e" }
        System.exit(1)
    }
}

@Serializable
data class ErrorResponse(val message: String)

fun getServer(application: Application, authSecretKey: SecretKey): NettyApplicationEngine {
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
            configureAuthentication(application.config.security.login, authSecretKey)
            configureRouting(application, authSecretKey)
            configureStatusPages()
            configureSessions()

            this.environment.monitor.subscribe(ApplicationStarted) { onServerStarted(application) }
        }
    }

    return embeddedServer(Netty, environment)
}

data class LoginCredentials(val username: String, val passwordHash: Hash)

suspend fun getLoginCredentials(
    storagePath: Path,
    serverConfig: ServerConfig,
    loginConfig: LoginConfig,
    authSecretKey: SecretKey,
): LoginCredentials {
    val loginCredentials = CompletableDeferred<LoginCredentials>()

    if (loginConfig.password != null) {
        return LoginCredentials(loginConfig.username, Sha256Hash.ofString(loginConfig.password!!))
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
            configureBootstrapRouting(storagePath, serverConfig, authSecretKey, loginCredentials)
            configureSessions()
        }
    }

    bootstrapInit(storagePath, serverConfig.ssl!!)
    val server = embeddedServer(Netty, environment).start(wait = false)
    val credentials = loginCredentials.await()
    server.stop(500, 500)

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

        val authSecretKey = generateAesKey()
        val loginCredentials = getLoginCredentials(storagePath, config.server, config.security.login, authSecretKey)
        val application = getApplication(storagePath, config, loginCredentials)
        getServer(application, authSecretKey).start()
    }
}