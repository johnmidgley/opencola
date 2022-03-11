package opencola.server

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import opencola.core.config.Application
import opencola.core.config.ServerConfig
import opencola.core.config.loadConfig
import opencola.core.model.Id
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
import kotlin.io.path.Path

fun getServer(serverConfig: ServerConfig): NettyApplicationEngine {
    return embeddedServer(Netty, port = serverConfig.port, host = serverConfig.host) {
        // install(CallLogging)
        configureHTTP()
        configureContentNegotiation()
        configureRouting()
    }
}

fun main() {
    val applicationPath = Path(System.getProperty("user.dir"))
    val config = loadConfig(applicationPath.resolve( "opencola-server.yaml"))
    val publicKey = Application.getOrCreateRootPublicKey(applicationPath.resolve(config.storage.path), config)

    Application.instance = Application.instance(Application.getStoragePath(applicationPath, config),  config, publicKey)
    Application.instance.logger.info("Application authority: ${Id.ofPublicKey(publicKey)}")
    val serverConfig = config.server ?: throw RuntimeException("Server config not specified")

    // TODO: Make sure entityService starts as soon as server is up, so that transactions can be received

    getServer(serverConfig).start(wait = true)
}
