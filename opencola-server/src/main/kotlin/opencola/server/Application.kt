package opencola.server

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import opencola.core.config.Application
import opencola.core.config.Config
import opencola.core.config.loadConfig
import opencola.core.content.TextExtractor
import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.toHexString
import opencola.core.model.Authority
import opencola.core.search.SearchIndex
import opencola.core.security.*
import opencola.core.storage.ExposedEntityStore
import opencola.core.storage.LocalFileStore
import opencola.core.storage.SQLiteDB
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
import opencola.service.search.SearchService
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import java.nio.file.Path
import java.security.KeyPair
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun getAuthorityKeyPair(): KeyPair {
    // TODO: Need to be able to generate a new authority. Should be triggered in chrome extension settings
    val authorityPublicKey = publicKeyFromBytes("3059301306072a8648ce3d020106082a8648ce3d03010703420004484a53f3dc6cecead248e0f299df8e191157010ac0892ef15a4158b8babd32eca522dc1c307578e5f0f76917c43795b775c4feba2f6007940a40f52efef5ffba".hexStringToByteArray())
    val authorityPrivateKey = privateKeyFromBytes("3041020100301306072a8648ce3d020106082a8648ce3d0301070427302502010104204158f0d52ed288ae60a84f8dc250b77d0c7263b336fd403b084618269285b172".hexStringToByteArray())
    return KeyPair(authorityPublicKey, authorityPrivateKey)
}

// TODO: This shouldn't be here. It should be core functionality
fun getAuthority(path: Path, config: Config): Authority {
    val authorityPubPath = path.resolve(config.storage.path).resolve("authority.pub")

    if(!authorityPubPath.exists()) {
        //TODO: Move to identity service
        val keyPair = generateKeyPair()
        val authority = Authority(keyPair.public)

        val keyStore = KeyStore(
            path.resolve(config.storage.path).resolve(config.security.keystore.name),
            config.security.keystore.password
        )

        keyStore.addKey(authority.authorityId, keyPair)
        authorityPubPath.writeText(keyPair.public.encoded.toHexString())
    }

    val authority = Authority(publicKeyFromBytes(authorityPubPath.readText().hexStringToByteArray()))

    return authority

}

fun main() {
    val applicationPath = Path(System.getProperty("user.dir"))
    val config = loadConfig(applicationPath.resolve( "opencola-server.yaml"))
    val authority = getAuthority(applicationPath, config)
    val keyStore = KeyStore(
        applicationPath.resolve(config.storage.path).resolve(config.security.keystore.name),
        config.security.keystore.password
    )
    val sqLiteDB = SQLiteDB(applicationPath.resolve(config.storage.path).resolve("${authority.authorityId}.db")).db

    val injector = DI {
        bindSingleton { authority }
        bindSingleton { keyStore }
        bindSingleton { Signator(instance()) }
        bindSingleton { ExposedEntityStore(instance(), instance(), sqLiteDB) }
        bindSingleton { LocalFileStore(applicationPath.resolve(config.storage.path).resolve("filestore")) }
        bindSingleton { SearchIndex(instance())}
        bindSingleton { SearchService(instance(), instance(), instance()) }
        bindSingleton { TextExtractor() }
        bindSingleton { DataHandler(instance(), instance(), instance()) }
    }

    Application.instance = Application("opencola.server", applicationPath, config, injector)
    val serverConfig = config.server ?: throw RuntimeException("Server config not specified")

    embeddedServer(Netty, port = serverConfig.port, host = serverConfig.host) {
        install(CallLogging)
        configureHTTP()
        configureContentNegotiation()
        configureRouting()
        log.info("Application authority: ${authority.authorityId}")
    }.start(wait = true)
}
