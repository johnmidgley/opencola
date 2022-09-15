package opencola.server

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import mu.KotlinLogging
import io.opencola.core.config.Application
import io.opencola.core.config.loadConfig
import io.opencola.core.event.EventBus
import io.opencola.core.event.Events
import io.opencola.core.model.Id
import io.opencola.core.network.NetworkNode
import io.opencola.core.security.encode
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("opencola")

fun onServerStarted(application: Application){
    application.inject<NetworkNode>().start()
    application.inject<EventBus>().sendMessage(Events.NodeStarted.toString())
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

    val config = loadConfig(storagePath.resolve("opencola-server.yaml"))
    val keyPair = Application.getOrCreateRootKeyPair(storagePath, config.security)

    val application = Application.instance(applicationPath, storagePath, config, keyPair)
    application.logger.info("Authority: ${Id.ofPublicKey(keyPair.public)}")
    application.logger.info("Public Key : ${keyPair.public.encode()}")

    // TODO: Make sure entityService starts as soon as server is up, so that transactions can be received
    getServer(application).start(wait = true)
}
