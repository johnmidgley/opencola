package opencola.core.config

import opencola.core.network.stopNetwork
import org.kodein.di.DI
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

class Application(val path: Path, val config: Config, val injector: DI) {
    val storagePath: Path get() = path.resolve(config.storage.path)

    companion object Global {
        private var application: Application? = null
        var instance: Application
            get() {
                return application ?: throw IllegalStateException("Attempt to get an uninitialized application")
            }
            set(value) {
                if (application != null)
                    throw IllegalStateException("Attempt to reset global application")

                application = value
            }
    }
}