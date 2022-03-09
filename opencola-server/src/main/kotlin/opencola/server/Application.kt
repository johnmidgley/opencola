package opencola.server

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import opencola.core.config.Application
import opencola.core.config.loadConfig
import opencola.core.extensions.hexStringToByteArray
import opencola.core.model.Id
import opencola.core.security.*
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
import java.security.KeyPair
import kotlin.io.path.Path

fun getAuthorityKeyPair(): KeyPair {
    // TODO: Need to be able to generate a new authority. Should be triggered in chrome extension settings
    val authorityPublicKey = publicKeyFromBytes("3059301306072a8648ce3d020106082a8648ce3d03010703420004484a53f3dc6cecead248e0f299df8e191157010ac0892ef15a4158b8babd32eca522dc1c307578e5f0f76917c43795b775c4feba2f6007940a40f52efef5ffba".hexStringToByteArray())
    val authorityPrivateKey = privateKeyFromBytes("3041020100301306072a8648ce3d020106082a8648ce3d0301070427302502010104204158f0d52ed288ae60a84f8dc250b77d0c7263b336fd403b084618269285b172".hexStringToByteArray())
    return KeyPair(authorityPublicKey, authorityPrivateKey)
}

fun main() {
    val applicationPath = Path(System.getProperty("user.dir"))
    val config = loadConfig(applicationPath.resolve( "opencola-server.yaml"))
    val publicKey = Application.getOrCreateRootPublicKey(applicationPath.resolve(config.storage.path), config)

    Application.instance = Application.instance(Application.getStoragePath(applicationPath, config),  config, publicKey)
    val serverConfig = config.server ?: throw RuntimeException("Server config not specified")

    embeddedServer(Netty, port = serverConfig.port, host = serverConfig.host) {
        install(CallLogging)
        configureHTTP()
        configureContentNegotiation()
        configureRouting()
        log.info("Application authority: ${Id.ofPublicKey(publicKey)}")
    }.start(wait = true)
}
