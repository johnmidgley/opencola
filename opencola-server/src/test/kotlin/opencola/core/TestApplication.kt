package opencola.core

import getAuthority
import opencola.core.config.Application
import opencola.core.config.loadConfig
import opencola.core.security.KeyStore
import opencola.core.security.Signator
import opencola.server.getAuthorityKeyPair
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import java.util.*
import kotlin.io.path.Path

object TestApplication {
    val runUUID: UUID = UUID.randomUUID()

    init {
        val applicationPath = Path(System.getProperty("user.dir"))
        val config = loadConfig(applicationPath.resolve("opencola-test.yaml"))
        val authority = getAuthority()
        val keyStore = KeyStore(
            applicationPath.resolve(config.storage.path).resolve("$runUUID.${config.security.keystore.name}"),
            config.security.keystore.password
        )
        keyStore.addKey(authority.authorityId, getAuthorityKeyPair())

        val injector = DI {
            bindSingleton { authority }
            bindSingleton { keyStore }
            bindSingleton { Signator(instance()) }
        }

        Application.instance = Application(applicationPath, config, injector)
    }

    fun init(): Application {
        return Application.instance
    }
}