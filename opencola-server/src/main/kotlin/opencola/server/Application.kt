package opencola.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.opencola.core.config.*
import io.opencola.core.config.Application
import io.opencola.core.event.EventBus
import io.opencola.core.event.Events
import io.opencola.core.model.Id
import io.opencola.core.network.NetworkNode
import io.opencola.core.security.*
import io.opencola.core.serialization.Base58
import io.opencola.core.system.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
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
import java.net.NetworkInterface
import java.net.URI
import java.nio.file.Path
import java.security.KeyStore
import kotlin.concurrent.thread
import kotlin.io.path.*

private val logger = KotlinLogging.logger("opencola")
private const val sslCertStorePassword = "password"

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

fun getSanEntriesFromNetworkInterfaces(): List<String> {
    return NetworkInterface.getNetworkInterfaces()
        .toList()
        .flatMap { networkInterface ->
            networkInterface.inetAddresses
                .toList()
                .filterIsInstance<Inet4Address>()
                .map { "ip:${it.hostAddress}" }
        }.toList()
        .plus("dns:localhost")
}

fun getSanEntries(sslConfig: SSLConfig): List<String> {
    // If subject alternative names are specified in config, as is the case when running in docker, use them,
    // otherwise generate from network interfaces
    return sslConfig.sans.ifEmpty { getSanEntriesFromNetworkInterfaces() }
}

fun createSSLCertificateAndStore(storagePath: Path, password: String, sslConfig: SSLConfig) {
    val certPath = storagePath.resolve("cert")
    val sans = getSanEntries(sslConfig)
    val keyPair = generateRSAKeyPair()
    val cert = generateSelfSignedV3Certificate("CN=opencola, O=OpenCola", sans, keyPair)
    val keystore = KeyStore.getInstance("PKCS12","BC")
    keystore.load(null, password.toCharArray())
    keystore.setKeyEntry("opencola-ssl", keyPair.private, null, arrayOf(cert))

    // Write keystore for SSL use
    val keyStoreFile = storagePath.resolve("cert/opencola-ssl.pks")
    keyStoreFile.outputStream().use {
        keystore.store(it, password.toCharArray())
    }

    // Write certs to be used by devices
    certPath.resolve("opencola-ssl.pem").writeText(convertCertificateToPEM(cert))
    certPath.resolve("opencola-ssl.der").writeBytes(cert.encoded)

    logger.info { "Create cert with SANS: ${sans.joinToString(", " )}" }
}



fun getSSLCertificateStore(storagePath: Path, password: String, sslConfig: SSLConfig): KeyStore {
    val certStoragePath = storagePath.resolve("cert")
    if(!certStoragePath.isDirectory()) {
        throw IllegalStateException("'cert' directory doesn't exist in $certStoragePath. Please copy from install storage directory")
    }

    val keyStoreFile = certStoragePath.resolve("opencola-ssl.pks").toFile()
    if(!keyStoreFile.exists()) {
        logger.info { "SSL Certificate store not found - creating" }
        createSSLCertificateAndStore(storagePath, password, sslConfig)
        // openFile(certStoragePath.resolve("opencola-ssl.der"))
    }

    val keystore = KeyStore.getInstance("PKCS12","BC")
    keyStoreFile.inputStream().use { keystore.load(keyStoreFile.inputStream(), password.toCharArray()) }

    return keystore
}

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

fun getDefaultStoragePathForOS(): Path {
    val userHome = Path(System.getProperty("user.home"))

    return when (getOS()) {
        OS.Mac -> userHome.resolve("Library/Application Support/OpenCola/storage")
        OS.Windows -> userHome.resolve("AppData/Local/OpenCola/storage")
        else -> userHome.resolve(".opencola/storage")
    }
}

fun initStorage(argPath: String) : Path {
    val homeStoragePath = Path(System.getProperty("user.home")).resolve(".opencola/storage")

    val storagePath =
        if(argPath.isNotBlank()) {
            // Storage path has been explicitly set, so use it
            Path(argPath)
        } else if(homeStoragePath.exists()) {
            // The default original storage location is present, so use it
            homeStoragePath
        } else {
            // Fall back to OS specific default paths
            getDefaultStoragePathForOS()
        }

    if(!storagePath.exists()) {
        copyResources("storage", storagePath)
    }

    return storagePath
}


fun main(args: Array<String>) {
    try {
        runBlocking {
            // https://github.com/Kotlin/kotlinx-cli
            val parser = ArgParser("oc")

            // TODO: App parameter is now ignored. Was only needed to locate resources, which are now bundled directly.
            //  Leaving here until no scripts depend on it
            @Suppress("UNUSED_VARIABLE")
            var app by parser.option(ArgType.String, shortName = "a", description = "Application path").default(".")
            val storage by parser.option(ArgType.String, shortName = "s", description = "Storage path").default("")
            parser.parse(args)

            val storagePath = initStorage(storage)
            val config = loadConfig(storagePath.resolve("opencola-server.yaml"))
            logger.info { "OS:  ${System.getProperty("os.name")}" }
            logger.info { "Storage path: $storagePath" }
            initProvider()

            launch {
                delay(1000)
                openUri(URI("http://localhost:${config.server.port}"))
            }

            val loginCredentials = getLoginCredentials(storagePath, config.server, config.security.login)
            val application = getApplication(storagePath, config, loginCredentials)

            // TODO: Make sure entityService starts as soon as server is up, so that transactions can be received
            getServer(application, loginCredentials).start()
        }
    } catch (e: Exception) {
        logger.error { e }
    }
}
