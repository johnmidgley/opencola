package opencola.core.config

import com.sksamuel.hoplite.ConfigLoader
import java.nio.file.Path

// TODO: Add config layers, that allow for override. Use push / pop
// https://github.com/sksamuel/hoplite
data class Server(val host: String, val port: Int)
data class Filestore(val name: String = "filestore")
data class Storage(val path: Path, val filestore: Filestore = Filestore())
data class Keystore(val name: String, val password: String)
data class Security(val keystore: Keystore)
data class Peer(val id: String, val name: String, val ip: String)
data class Network(val peers: List<Peer>)
data class Config(val env: String,
                  val server: Server?,
                  val storage: Storage,
                  val security: Security,
                  val network: Network)

fun loadConfig(path: Path) : Config {
    return ConfigLoader().loadConfigOrThrow(path)
}

