package opencola.core

import opencola.core.config.Application
import opencola.core.config.loadConfig
import opencola.core.extensions.hexStringToByteArray
import opencola.core.model.Authority
import opencola.core.search.SearchIndex
import opencola.core.security.KeyStore
import opencola.core.security.privateKeyFromBytes
import opencola.core.security.publicKeyFromBytes
import org.kodein.di.instance
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.time.Instant
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.createDirectory

object TestApplication {
    private val authorityPublicKey = publicKeyFromBytes("3059301306072a8648ce3d020106082a8648ce3d030107034200043afa5d5e418d40dcce131c15cc0338e2be043584b168f3820ddc120259641973edff721756948b0bb8833b486fbde224b5e4987432383f79c3e013ebc40f0dc3".hexStringToByteArray())
    private val authorityPrivateKey = privateKeyFromBytes("3041020100301306072a8648ce3d020106082a8648ce3d03010704273025020101042058d9eb4708471a6189dcd6a5e37a724c158be8e820d90a1050f7a1d5876acf58".hexStringToByteArray())

    val applicationPath = Path(System.getProperty("user.dir"))
    val testRunName = Instant.now().epochSecond.toString()

    val instance by lazy {
        val authority = Authority(authorityPublicKey)
        val keyStore = KeyStore(
            config.storage.path.resolve(config.security.keystore.name),
            config.security.keystore.password
        )
        keyStore.addKey(authority.authorityId, KeyPair(authorityPublicKey, authorityPrivateKey))
        val instance =  Application.instance(config, authorityPublicKey)
        val index by instance.injector.instance<SearchIndex>()

        // Clear out any existing index
        index.delete()
        index.create()

        // Return the instance
        instance
    }

    val config by lazy {
        val baseConfig = loadConfig(applicationPath, "opencola-test.yaml")
        baseConfig.setStoragePath(applicationPath.resolve(baseConfig.storage.path).resolve(testRunName))
    }

    fun getTmpFilePath(suffix: String): Path {
        return config.storage.path.resolve("${UUID.randomUUID()}$suffix")
    }

    fun createStorageDirectory(name: String) : Path {
        return config.storage.path.resolve("$name").createDirectory()
    }
}