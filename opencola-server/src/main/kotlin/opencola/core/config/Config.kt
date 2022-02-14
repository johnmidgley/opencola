package opencola.core.config

import com.sksamuel.hoplite.ConfigLoader
import java.net.URI
import java.nio.file.Path

// TODO: Add config layers, that allow for override. Use push / pop
// https://github.com/sksamuel/hoplite
data class Server(val host: String, val port: Int)
data class Storage(val path: Path)
data class Keystore(val name: String, val password: String)
data class Security(val keystore: Keystore)
data class Config(val env: String,
                  val server: Server?,
                  val storage: Storage,
                  val security: Security)


