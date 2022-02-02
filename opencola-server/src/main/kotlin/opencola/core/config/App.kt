package opencola.core.config

import kotlin.io.path.Path

object App {
    var path = Path(System.getProperty("user.dir"))
    val config = Config()
}