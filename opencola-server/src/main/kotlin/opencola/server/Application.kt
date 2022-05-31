package opencola.server

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import mu.KotlinLogging
import opencola.core.config.Application
import opencola.core.config.loadConfig
import opencola.core.event.EventBus
import opencola.core.event.Events
import opencola.core.model.Id
import opencola.core.security.encode
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
import org.kodein.di.instance
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("opencola")

fun onServerStarted(application: Application){
    val eventBus by application.injector.instance<EventBus>()
    eventBus.sendMessage(Events.NodeStarted.toString())
}

fun getServer(application: Application): NettyApplicationEngine {
    val serverConfig = application.config.server

    return embeddedServer(Netty, port = serverConfig.port, host = serverConfig.host) {
        // install(CallLogging)
        configureHTTP()
        configureContentNegotiation()
        configureRouting(application)
        this.environment.monitor.subscribe(ApplicationStarted) { onServerStarted(application) }
    }
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

    val config = loadConfig(storagePath, "opencola-server.yaml")
    val publicKey = Application.getOrCreateRootPublicKey(storagePath, config.security)

    val application = Application.instance(applicationPath, storagePath, config, publicKey)
    application.logger.info("Authority: ${Id.ofPublicKey(publicKey)}")
    application.logger.info("Public Key : ${publicKey.encode()}")

    // TODO: Make sure entityService starts as soon as server is up, so that transactions can be received
    getServer(application).start(wait = true)
}
