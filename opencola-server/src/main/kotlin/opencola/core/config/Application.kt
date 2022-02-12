package opencola.core.config

import org.kodein.di.DI
import java.nio.file.Path

class Application(val path: Path, val config: Config, val injector: DI) {
    val storagePath get() = path.resolve(config.storage.path)

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