package opencola.core.config

import com.sksamuel.hoplite.ConfigLoader
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

// TODO: Add config layers, that allow for override. Use push / pop
// https://github.com/sksamuel/hoplite
data class Server(val host: String, val port: Int)

data class Filestore(val name: String = "filestore")

data class Storage(val path: Path, val filestore: Filestore = Filestore()){
    init{
        //TODO: Don't create filestore here - make Filestore do this when needed.
        listOf(path, path.resolve(filestore.name)).forEach{
            if(!it.exists())
                it.createDirectory()
        }
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

