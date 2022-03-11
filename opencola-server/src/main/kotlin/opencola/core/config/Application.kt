package opencola.core.config

import mu.KotlinLogging
import opencola.core.content.TextExtractor
import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.toHexString
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.search.SearchIndex
import opencola.core.security.KeyStore
import opencola.core.security.Signator
import opencola.core.security.generateKeyPair
import opencola.core.security.publicKeyFromBytes
import opencola.core.storage.ExposedEntityStore
import opencola.core.storage.LocalFileStore
import opencola.core.storage.NetworkedEntityStore
import opencola.core.storage.SQLiteDB
import opencola.server.DataHandler
import opencola.service.PeerService
import opencola.service.search.SearchService
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import java.nio.file.Path
import java.security.PublicKey
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class Application(val storagePath: Path, val config: Config, val injector: DI) {
    val logger = KotlinLogging.logger("opencola.${config.env}")

    companion object Global {
        val logger = KotlinLogging.logger("opencola.init")

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

        fun getStoragePath(applicationPath: Path, config: Config): Path {
            val storagePath = applicationPath.resolve(config.storage.path)

            if(!storagePath.exists()){
                storagePath.createDirectory()
            }

            return storagePath
        }

        // TODO: pub key should come from private store, not authority.pub, and multiple authorities (personas) should be allowed
        fun getOrCreateRootPublicKey(storagePath: Path, config: Config): PublicKey {
            val publicKeyFile = "authority.pub" // TODO: Config?
            val authorityPubPath = storagePath.resolve(publicKeyFile)
            val keyStore = KeyStore(storagePath.resolve(config.security.keystore.name), config.security.keystore.password)
            val publicKey =  if (authorityPubPath.exists()) {
                val publicKey = publicKeyFromBytes(authorityPubPath.readText().hexStringToByteArray())
                val privateKey = keyStore.getPrivateKey(Id.ofPublicKey(publicKey))
                    ?: throw IllegalStateException("No private key found in keystore {${keyStore.path}} for public key {${publicKey}} found in $publicKeyFile")
                publicKey
            } else {
                logger.info { "Key file $publicKeyFile doesn't exist. Creating new KeyPair" }
                val keyPair = generateKeyPair()
                keyStore.addKey(Id.ofPublicKey(keyPair.public), keyPair)
                authorityPubPath.writeText(keyPair.public.encoded.toHexString())
                keyPair.public
            }

            return publicKey
        }

        fun instance(storagePath: Path, config: Config, authorityPublicKey: PublicKey): Application {
            // TODO: Change from authority to public key - they authority should come from the private store based on the private key
            val authority = Authority(authorityPublicKey)
            val keyStore = KeyStore(storagePath.resolve(config.security.keystore.name), config.security.keystore.password)
            val fileStore = LocalFileStore(storagePath.resolve(config.storage.filestore.name))
            val sqLiteDB = SQLiteDB(storagePath.resolve("${authority.authorityId}.db")).db

            val injector = DI {
                bindSingleton { authority }
                bindSingleton { keyStore }
                bindSingleton { fileStore }
                bindSingleton { TextExtractor() }
                bindSingleton { Signator(instance()) }
                bindSingleton { SearchIndex(instance()) }
                bindSingleton("base") { ExposedEntityStore(instance(), instance(), sqLiteDB) }
                bindSingleton { PeerService(config.network) }
                bindSingleton { NetworkedEntityStore(instance("base"), instance()) }
                bindSingleton { SearchService(instance(), instance(), instance()) }
                // TODO: Add unit test for data handler
                bindSingleton { DataHandler(instance(), instance(), instance()) }
            }

            return Application(storagePath, config, injector)
        }
    }
}