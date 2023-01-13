package opencola.core

import io.opencola.core.config.Application
import io.opencola.core.config.loadConfig
import io.opencola.core.model.Authority
import io.opencola.core.search.SearchIndex
import io.opencola.security.KeyStore
import io.opencola.security.decodePrivateKey
import io.opencola.security.decodePublicKey
import org.kodein.di.instance
import java.net.URI
import java.nio.file.Path
import java.security.KeyPair
import java.time.Instant
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

object TestApplication {
    private val authorityPublicKey = decodePublicKey("3059301306072a8648ce3d020106082a8648ce3d030107034200043afa5d5e418d40dcce131c15cc0338e2be043584b168f3820ddc120259641973edff721756948b0bb8833b486fbde224b5e4987432383f79c3e013ebc40f0dc3")
    private val authorityPrivateKey = decodePrivateKey("3041020100301306072a8648ce3d020106082a8648ce3d03010704273025020101042058d9eb4708471a6189dcd6a5e37a724c158be8e820d90a1050f7a1d5876acf58")

    val applicationPath = Path(System.getProperty("user.dir"))
    val testRunName = Instant.now().epochSecond.toString()
    val storagePath: Path = applicationPath.resolve("../../test/storage/").resolve(testRunName)

    init{
        if(!storagePath.exists()){
            storagePath.createDirectory()
        }
    }

    val instance by lazy {
        val authority = Authority(authorityPublicKey, URI("http://test"), "Test Authority")
        val keyStore = KeyStore(
            storagePath.resolve("keystore.pks"),
            "password"
        )
        val keyPair = KeyPair(authorityPublicKey, authorityPrivateKey)
        keyStore.addKey(authority.authorityId.toString(), keyPair)
        val instance =  Application.instance(storagePath, config, keyPair, "password")
        val index by instance.injector.instance<SearchIndex>()

        // Clear out any existing index
        index.destroy()
        index.create()

        // Return the instance
        instance
    }

    val config by lazy {
        loadConfig(applicationPath.resolve("../../test/storage/opencola-test.yaml"))
    }

    fun getTmpFilePath(suffix: String): Path {
        return storagePath.resolve("${UUID.randomUUID()}$suffix")
    }

    fun getTmpDirectory(suffix: String): Path {
        return storagePath.resolve("${UUID.randomUUID()}$suffix").createDirectory()
    }

    fun createStorageDirectory(name: String) : Path {
        return storagePath.resolve(name).createDirectory()
    }

    fun newApplication(): Application {
        val applicationStoragePath = getTmpDirectory(".storage")
        val publicKey = Application.getOrCreateRootKeyPair(applicationStoragePath, "password")
        return Application.instance(applicationStoragePath, config, publicKey, "password")
    }
}