package opencola.core.config

import com.sksamuel.hoplite.ConfigLoader
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

// TODO: Add config layers, that allow for override. Use push / pop
// https://github.com/sksamuel/hoplite
data class Server(val host: String, val port: Int)

data class Filestore(val name: String = "filestore")

data class Storage(val path: Path, val filestore: Filestore = Filestore()) {
    //TODO: This shouldn't really be here - it should be in the application. It's
    //here for convenience, since some tests use config and don't want to initialize the whole
    //app. Lazy initializing the app (i.e. DI) would be better.
    init {
        if (!path.exists())
            path.createDirectory()
    }
}

data class Keystore(val name: String, val password: String)

data class Security(val keystore: Keystore)

data class Config(val env: String,
                  val server: Server?,
                  val storage: Storage,
                  val security: Security)

fun loadConfig(path: Path) : Config {
    return ConfigLoader().loadConfigOrThrow(path)
}

