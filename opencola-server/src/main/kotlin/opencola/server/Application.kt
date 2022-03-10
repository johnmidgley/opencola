package opencola.server

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import opencola.core.config.Application
import opencola.core.config.Server
import opencola.core.config.loadConfig
import opencola.core.extensions.hexStringToByteArray
import opencola.core.model.Id
import opencola.core.security.*
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
import java.security.KeyPair
import kotlin.io.path.Path

fun getServer(serverConfig: Server): NettyApplicationEngine {
    return embeddedServer(Netty, port = serverConfig.port, host = serverConfig.host) {
        install(CallLogging)
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

    getServer(serverConfig).start(wait = true)
}
