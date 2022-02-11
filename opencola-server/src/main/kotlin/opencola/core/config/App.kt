package opencola.core.config

import kotlin.io.path.Path

object App {
    var path = Path(System.getProperty("user.dir"))

    fun getConfig(): Config {
        return ConfigRoot.config ?: throw IllegalStateException("Attempt to access uninitialized config")
    }
}