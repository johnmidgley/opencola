package opencola.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.opencola.application.Application
import io.opencola.application.Config
import io.opencola.application.LoginConfig
import io.opencola.application.ServerConfig
import io.opencola.event.bus.EventBus
import io.opencola.event.bus.Events
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
import opencola.server.plugins.routing.configureBootstrapRouting
import opencola.server.plugins.routing.configureRouting
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.URI
import java.nio.file.Path
import javax.crypto.SecretKey
import kotlin.system.exitProcess
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

fun enableResumeDetection(application: Application) {
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
}

fun onServerStarted(application: Application) {
    try {
        val hostAddress = Inet4Address.getLocalHost().hostAddress
        enableResumeDetection(application)
        logger.info { "Server started: http://$hostAddress:${application.config.server.port}" }
        application.config.server.ssl?.let {
            logger.info { "Server started: https://$hostAddress:${it.port} - certs needed" }
        }
    } catch (e: Throwable) {
        logger.error { "Error starting server: $e" }
        exitProcess(1)
    }
}

@Serializable
data class ErrorResponse(val message: String)

fun getBootstrapEnvironment(
    storagePath: Path,
    serverConfig: ServerConfig,
    loginConfig: LoginConfig,
    loginCredentials: CompletableDeferred<LoginCredentials>,
    authSecretKey: SecretKey = generateAesKey()
): ApplicationEngineEnvironment {
    return applicationEngineEnvironment {
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
            configureContentNegotiation()
            configureSessions()
            configureStatusPages()
            configureAuthentication(loginConfig.authenticationRequired, authSecretKey)
            configureBootstrapRouting(storagePath, serverConfig, loginConfig, authSecretKey, loginCredentials)
        }
    }
}

fun getServer(application: Application, authSecretKey: SecretKey): NettyApplicationEngine {
    val serverConfig = application.config.server

    // TODO: Should be able to just use getBootstrapEnvironment() but something's not right here
//    val environment = getBootstrapEnvironment(
//        application.storagePath,
//        application.config.server,
//        application.config.security.login,
//        CompletableDeferred(),
//        authSecretKey
//    )
//    environment.application.configureRouting(application)
//    environment.monitor.subscribe(ApplicationStarted) { onServerStarted(application) }

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
            configureAuthentication(application.config.security.login.authenticationRequired, authSecretKey)
            configureRouting(application)
            configureStatusPages()
            configureSessions()

            this.environment.monitor.subscribe(ApplicationStarted) { onServerStarted(application) }
        }
    }

    return embeddedServer(Netty, environment)
}

data class LoginCredentials(val username: String, val passwordHash: Hash)



fun startServer(storagePath: Path, config: Config) {
    runBlocking {
        if (!runningInDocker()) {
            launch {
                delay(1000)
                openUri(URI("http://localhost:${config.server.port}/start"))
            }
        }

        bootstrapInit(storagePath, config.server.ssl!!)
        val loginConfig = config.security.login
        val deferredLoginCredentials = CompletableDeferred<LoginCredentials>()
        val environment = getBootstrapEnvironment(storagePath, config.server, loginConfig, deferredLoginCredentials)
        embeddedServer(Netty, environment).also { it.start() }

        if (loginConfig.password != null)
            deferredLoginCredentials.complete(
                LoginCredentials(loginConfig.username, Sha256Hash.ofString(loginConfig.password!!))
            )

        val application = getApplication(storagePath, config, deferredLoginCredentials.await())
        environment.application.configureRouting(application)
        enableResumeDetection(application)
    }
}