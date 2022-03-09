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
            testRunStoragePath.resolve(config.security.keystore.name),
            config.security.keystore.password
        )
        keyStore.addKey(authority.authorityId, KeyPair(authorityPublicKey, authorityPrivateKey))
        Application.instance = Application.instance(testRunStoragePath, config, authorityPublicKey)
        val index by Application.instance.injector.instance<SearchIndex>()

        // Clear out any existing index
        index.delete()
        index.create()

        // Return the instance
        Application.instance
    }

    val config by lazy {
        loadConfig(applicationPath.resolve("opencola-test.yaml"))
    }

    val testRunStoragePath: Path by lazy {
        val path = applicationPath.resolve(config.storage.path).resolve(testRunName)
        Files.createDirectories(path)
        path
    }

    fun getTmpFilePath(suffix: String): Path {
        return testRunStoragePath.resolve("${UUID.randomUUID()}$suffix")
    }
}