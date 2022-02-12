package opencola.core.config

import com.sksamuel.hoplite.ConfigLoader
import java.nio.file.Path

// TODO: Add config layers, that allow for override. Use push / pop
// https://github.com/sksamuel/hoplite
data class Storage(val path: Path)
data class Keystore(val name: String, val password: String)
data class Security(val keystore: Keystore)
data class Config(val env: String,
                  val storage: Storage,
                  val security: Security)

object ConfigRoot {
    // TODO: This should be injected
    var config: Config? = null

    fun load(path: Path) {
        config = ConfigLoader().loadConfigOrThrow(path)
    }
}

