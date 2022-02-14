package opencola.server

import com.sksamuel.hoplite.ConfigLoader
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import opencola.core.config.Application
import opencola.core.config.Config
import opencola.core.content.TextExtractor
import opencola.core.extensions.hexStringToByteArray
import opencola.core.model.Authority
import opencola.core.search.SearchIndex
import opencola.core.security.KeyStore
import opencola.core.security.Signator
import opencola.core.security.privateKeyFromBytes
import opencola.core.security.publicKeyFromBytes
import opencola.core.storage.LocalFileStore
import opencola.core.storage.SimpleEntityStore
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
import opencola.service.search.SearchService
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import java.security.KeyPair
import kotlin.io.path.Path

fun getAuthorityKeyPair(): KeyPair {
    // TODO: Need to be able to generate a new authority. Should be triggered in chrome extension settings
    val authorityPublicKey = publicKeyFromBytes("3059301306072a8648ce3d020106082a8648ce3d03010703420004484a53f3dc6cecead248e0f299df8e191157010ac0892ef15a4158b8babd32eca522dc1c307578e5f0f76917c43795b775c4feba2f6007940a40f52efef5ffba".hexStringToByteArray())
    val authorityPrivateKey = privateKeyFromBytes("3041020100301306072a8648ce3d020106082a8648ce3d0301070427302502010104204158f0d52ed288ae60a84f8dc250b77d0c7263b336fd403b084618269285b172".hexStringToByteArray())
    return KeyPair(authorityPublicKey, authorityPrivateKey)
}

fun getAuthority(): Authority {
    val keyPair = getAuthorityKeyPair()
    return Authority(keyPair.public, name = "Authority")
}

fun main() {
    val path = Path(System.getProperty("user.dir"))
    val config: Config = ConfigLoader().loadConfigOrThrow(path.resolve("opencola-server.yaml"))

    val keyPair = getAuthorityKeyPair()
    val authority = Authority(keyPair.public)
    val keyStore = KeyStore(
        path.resolve(config.storage.path).resolve(config.security.keystore.name),
        config.security.keystore.password
    )
    keyStore.addKey(authority.authorityId, keyPair)

    val injector = DI {
        bindSingleton { authority }
        bindSingleton { keyStore }
        bindSingleton { Signator(instance()) }
        bindSingleton { SimpleEntityStore(instance(), instance())  }
        bindSingleton { LocalFileStore(path.resolve(config.storage.path).resolve("filestore")) }
        bindSingleton { SearchIndex(instance())}
        bindSingleton { SearchService(instance(), instance(), instance()) }
        bindSingleton { TextExtractor() }
    }

    Application.instance = Application(path, config, injector)
    val serverConfig = config.server ?: throw RuntimeException("Server config not specified")

    embeddedServer(Netty, port = serverConfig.port, host = serverConfig.host) {
        configureHTTP()
        configureContentNegotiation()
        configureRouting()
    }.start(wait = true)
}
