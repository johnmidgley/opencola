package opencola.core.config

import com.sksamuel.hoplite.ConfigLoader
import java.nio.file.Path

// TODO: Add config layers, that allow for override. Use push / pop
// https://github.com/sksamuel/hoplite
data class ServerConfig(val host: String, val port: Int)
data class FilestoreConfig(val name: String = "filestore")
data class StorageConfig(val path: Path, val filestore: FilestoreConfig = FilestoreConfig())
data class KeystoreConfig(val name: String, val password: String)
data class SecurityConfig(val keystore: KeystoreConfig)
data class PeerConfig(val id: String, val publicKey: String, val name: String, val host: String)
data class NetworkConfig(val peers: List<PeerConfig> = emptyList())
data class Config(val name: String,
                  val server: ServerConfig?,
                  val storage: StorageConfig,
                  val security: SecurityConfig,
                  val network: NetworkConfig){
    fun setServer(server: ServerConfig): Config {
        return Config(name, server, storage, security, network)
    }

    fun setStorage(storage: StorageConfig): Config {
        return Config(name, server, storage, security, network)
    }

    fun setSecurity(security: SecurityConfig): Config {
        return Config(name, server, storage, security, network)
    }

    fun setNetwork(network: NetworkConfig): Config {
        return Config(name, server, storage, security, network)
    }
}

fun loadConfig(path: Path) : Config {
    return ConfigLoader().loadConfigOrThrow(path)
}

