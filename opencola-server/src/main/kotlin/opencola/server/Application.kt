package opencola.server

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import mu.KotlinLogging
import io.opencola.core.config.Application
import io.opencola.core.config.SSLConfig
import io.opencola.core.config.loadConfig
import io.opencola.core.event.EventBus
import io.opencola.core.event.Events
import io.opencola.core.extensions.runCommand
import io.opencola.core.model.Id
import io.opencola.core.network.NetworkNode
import io.opencola.core.security.encode
import io.opencola.core.security.getMd5Digest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.nio.file.Path
import java.security.KeyStore
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

private val logger = KotlinLogging.logger("opencola")

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

fun getSanEntry(san: String) :String {
    return when(san) {
        "localhost" -> "dns:localhost"
        "hostAddress" -> "ip:${Inet4Address.getLocalHost().hostAddress}"
        else -> "ip:$san"
    }
}

fun getSSLCertificateStore(storagePath: Path, password: String, sslConfig: SSLConfig): KeyStore {
    val certStoragePath = storagePath.resolve("cert")
    if(!certStoragePath.isDirectory()) {
        throw IllegalStateException("'cert' directory doesn't exist in $certStoragePath. Please copy from install storage directory")
    }

    val keyStoreFile = storagePath.resolve("cert/opencola-ssl.jks").toFile()
    if(!keyStoreFile.exists()) {
        logger.info { "SSL Certificate store not found - creating" }
        val sans = sslConfig.sans.joinToString(",") { getSanEntry(it) }
        "./gen-ssl-cert $sans".runCommand(storagePath.resolve("cert"), printOutput = true)
    }

    val keystore = KeyStore.getInstance("JKS")!!
    keyStoreFile.inputStream().use { keystore.load(keyStoreFile.inputStream(), password.toCharArray()) }

    return keystore
}

fun getServer(application: Application): NettyApplicationEngine {
    val serverConfig = application.config.server
    val sslCertStorePassword = "password"
    val realm = "/"
    val username = application.config.security.login.username
    val userTable = mapOf(username to getMd5Digest("$username:$realm:password"))

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
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val response = ErrorResponse(cause.message ?: "Unknown")
                    call.respond(HttpStatusCode.InternalServerError, Json.encodeToString(response))
                }
            }
            install(Authentication) {
                digest("auth-digest") {
                    this.realm = realm
                    digestProvider { userName, realm ->
                        userTable[userName]
                    }
                }
            }
            this.environment.monitor.subscribe(ApplicationStarted) { onServerStarted(application) }
        }
    }

    return embeddedServer(Netty, environment)
}

fun main(args: Array<String>) {
    // https://github.com/Kotlin/kotlinx-cli
    val parser = ArgParser("oc")
    val app by parser.option(ArgType.String, shortName = "a", description = "Application path").default(".")
    val storage by parser.option(ArgType.String, shortName = "s", description = "Storage path").default("../storage")

    parser.parse(args)

    val currentPath = Path(System.getProperty("user.dir"))
    val applicationPath = currentPath.resolve(app)
    val storagePath = currentPath.resolve(storage)

    logger.info { "Application path: $applicationPath" }
    logger.info { "Storage path: $storagePath" }

    val config = loadConfig(storagePath.resolve("opencola-server.yaml"))
    val keyPair = Application.getOrCreateRootKeyPair(storagePath, config.security)

    val application = Application.instance(applicationPath, storagePath, config, keyPair)
    application.logger.info("Authority: ${Id.ofPublicKey(keyPair.public)}")
    application.logger.info("Public Key : ${keyPair.public.encode()}")

    // TODO: Make sure entityService starts as soon as server is up, so that transactions can be received
    getServer(application).start(wait = true)
}
