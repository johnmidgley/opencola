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
import opencola.service.EntityService
import opencola.core.network.PeerRouter
import opencola.core.storage.*
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
        // TODO: Remove - create loggers by component / namespace
        val logger = KotlinLogging.logger("opencola.init")

        fun getStoragePath(applicationPath: Path, config: Config): Path {
            val storagePath = applicationPath.resolve(config.storage.path)

            if(!storagePath.exists()){
                storagePath.createDirectory()
            }

            return storagePath
        }

        // TODO: pub key should come from private store, not authority.pub, and multiple authorities (personas) should be allowed
        // TODO: Move to Identity Service
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
                bindSingleton { AddressBook(instance(), config.network) }
                bindSingleton { PeerRouter(instance()) }
                bindSingleton { SearchIndex(instance()) }
                bindSingleton { ExposedEntityStore(instance(), instance(), instance(), sqLiteDB) }
                bindSingleton { SearchService(instance(), instance(), instance()) }
                bindSingleton { EntityService(instance(), instance(), instance(), instance(), instance(), instance()) }
                // TODO: Add unit tests for MhtCache
                // TODO: Get cache name from config
                bindSingleton { MhtCache(storagePath.resolve("mht-cache"), instance(), instance()) }
            }

            return Application(storagePath, config, injector)
        }
    }
}