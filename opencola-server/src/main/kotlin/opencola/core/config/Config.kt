package opencola.core.config

import com.sksamuel.hoplite.ConfigLoader
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

// TODO: Add config layers, that allow for override. Use push / pop
// https://github.com/sksamuel/hoplite
data class EventBusConfig(val name: String = "event-bus", val maxAttempts: Int = 3)
data class ServerConfig(val host: String, val port: Int)
data class FilestoreConfig(val name: String = "filestore")
data class StorageConfig(val path: Path, val filestore: FilestoreConfig = FilestoreConfig())
data class KeystoreConfig(val name: String, val password: String)
data class SecurityConfig(val keystore: KeystoreConfig)
data class SolrConfig(val baseUrl: String, val connectionTimeoutMillis: Int, val socketTimeoutMillis: Int)
data class SearchConfig(val solr: SolrConfig)
data class PeerConfig(val id: String, val publicKey: String, val name: String, val host: String, val active: Boolean = true)
data class NetworkConfig(val peers: List<PeerConfig> = emptyList())


data class Config(val name: String,
                  val eventBus: EventBusConfig,
                  val server: ServerConfig,
                  val storage: StorageConfig,
                  val security: SecurityConfig,
                  val search: SearchConfig,
                  val network: NetworkConfig,
                  ){
    fun setName(name: String): Config {
        return Config(name, eventBus, server, storage, security, search, network, )

    }

    fun setServer(server: ServerConfig): Config {
        return Config(name, eventBus, server, storage, security, search, network)
    }

    fun setStorage(storage: StorageConfig): Config {
        return Config(name, eventBus, server, storage, security, search, network)
    }

    fun setStoragePath(path: Path): Config {
        if(!path.exists()){
            path.createDirectory()
        }
        return Config(name, eventBus, server, StorageConfig(path, storage.filestore), security, search, network)
    }

    fun setSecurity(security: SecurityConfig): Config {
        return Config(name,eventBus, server, storage, security, search, network)
    }

    fun setNetwork(network: NetworkConfig): Config {
        return Config(name, eventBus, server, storage, security, search, network)
    }
}

fun loadConfig(applicationPath: Path, name: String): Config {
    val configPath = applicationPath.resolve(name)
    val baseConfig: Config = ConfigLoader().loadConfigOrThrow(configPath)
    val storagePath = applicationPath.resolve(baseConfig.storage.path)

    if(!storagePath.exists()){
        storagePath.createDirectory()
    }

    return baseConfig.setStoragePath(storagePath)
}


