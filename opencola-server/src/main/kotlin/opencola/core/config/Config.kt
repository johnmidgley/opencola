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
data class PeerConfig(val id: String, val name: String, val host: String)
data class NetworkConfig(val peers: List<PeerConfig> = emptyList())
data class Config(val env: String,
                  val server: ServerConfig?,
                  val storage: StorageConfig,
                  val security: SecurityConfig,
                  val network: NetworkConfig)

fun loadConfig(path: Path) : Config {
    return ConfigLoader().loadConfigOrThrow(path)
}

