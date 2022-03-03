package opencola.core

import com.sksamuel.hoplite.ConfigLoader
import getAuthority
import opencola.core.config.Application
import opencola.core.config.Config
import opencola.core.security.KeyStore
import opencola.core.security.Signator
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import java.util.*
import kotlin.io.path.Path

object TestApplication {
    val runUUID = UUID.randomUUID()

    init {
        val path = Path(System.getProperty("user.dir"))
        val config: Config = ConfigLoader().loadConfigOrThrow(path.resolve("opencola-test.yaml"))

        val injector = DI {
            bindSingleton { getAuthority() }
            bindSingleton {
                KeyStore(
                    path.resolve(config.storage.path).resolve("${TestApplication.runUUID}.${config.security.keystore.name}"),
                    config.security.keystore.password
                )}
            bindSingleton { Signator(instance()) }
        }

        Application.instance = Application(path, config, injector)
    }

    fun init(): Application {
        return Application.instance
    }
}