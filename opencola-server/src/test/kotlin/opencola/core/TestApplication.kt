package opencola.core

import getAuthority
import opencola.core.config.Application
import opencola.core.config.loadConfig
import opencola.core.content.TextExtractor
import opencola.core.search.SearchIndex
import opencola.core.security.KeyStore
import opencola.core.security.Signator
import opencola.core.storage.ExposedEntityStore
import opencola.core.storage.LocalFileStore
import opencola.core.storage.SQLiteDB
import opencola.server.getAuthorityKeyPair
import opencola.service.search.SearchService
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.createDirectory

object TestApplication {
    val testRunName = Instant.now().epochSecond.toString()

    init {
        val applicationPath = Path(System.getProperty("user.dir"))
        val config = loadConfig(applicationPath.resolve("opencola-test.yaml"))
        val storagePath = applicationPath.resolve(config.storage.path).resolve(testRunName.toString())

        storagePath.createDirectory()

        val authority = getAuthority()
        val keyStore = KeyStore(
            storagePath.resolve(config.security.keystore.name),
            config.security.keystore.password
        )
        keyStore.addKey(authority.authorityId, getAuthorityKeyPair())
        val fileStore = LocalFileStore(storagePath.resolve(config.storage.filestore.name))
        val sqLiteDB = SQLiteDB(storagePath.resolve("${authority.authorityId}.db")).db

        val injector = DI {
            bindSingleton { authority }
            bindSingleton { keyStore }
            bindSingleton { fileStore }
            bindSingleton { TextExtractor() }
            bindSingleton { Signator(instance()) }
            bindSingleton { SearchIndex(instance()) }
            bindSingleton { ExposedEntityStore(instance(), instance(), sqLiteDB) }
            bindSingleton { SearchService(instance(), instance(), instance()) }
        }

        val index by injector.instance<SearchIndex>()
        index.delete()
        index.create()

        Application.instance = Application(applicationPath, config, injector)
    }

    fun init(): Application {
        return Application.instance
    }
}