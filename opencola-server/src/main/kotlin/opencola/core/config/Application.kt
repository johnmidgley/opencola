package opencola.core.config

import mu.KotlinLogging
import opencola.core.content.TextExtractor
import opencola.core.event.EventBus
import opencola.core.event.MainReactor
import opencola.core.event.Reactor
import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.toHexString
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.security.KeyStore
import opencola.core.security.Signator
import opencola.core.security.generateKeyPair
import opencola.core.security.publicKeyFromBytes
import opencola.core.network.PeerRouter
import opencola.core.search.LuceneSearchIndex
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

class Application(val applicationPath: Path, val storagePath: Path, val config: Config, val injector: DI) {
    val logger = KotlinLogging.logger("opencola.${config.name}")

    companion object Global {
        // TODO: Remove - create loggers by component / namespace
        val logger = KotlinLogging.logger("opencola.init")

        // TODO: pub key should come from private store, not authority.pub, and multiple authorities (personas) should be allowed
        // TODO: Move to Identity Service
        fun getOrCreateRootPublicKey(storagePath: Path, securityConfig: SecurityConfig): PublicKey {
            val publicKeyFile = "authority.pub" // TODO: Config?
            val authorityPubPath = storagePath.resolve(publicKeyFile)
            val keyStore = KeyStore(storagePath.resolve(securityConfig.keystore.name), securityConfig.keystore.password)
            val publicKey =  if (authorityPubPath.exists()) {
                val publicKey = publicKeyFromBytes(authorityPubPath.readText().hexStringToByteArray())
                val privateKey = keyStore.getPrivateKey(Id.ofPublicKey(publicKey))
                if(privateKey != null)
                    logger.info { "Found private key in store" }
                else
                    throw IllegalStateException("No private key found in keystore {${keyStore.path}} for public key {${publicKey}} found in $publicKeyFile")

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

        fun instance(applicationPath: Path, storagePath: Path) : Application {
            if(!storagePath.exists()){
                logger.info { "Creating storage path: $storagePath" }
                storagePath.createDirectory()
            }

            val config = loadConfig(storagePath, "opencola-server.yaml")
            val publicKey = getOrCreateRootPublicKey(storagePath, config.security)

            return instance(applicationPath, storagePath, config, publicKey)
        }

        fun instance(applicationPath: Path, storagePath: Path, config: Config, authorityPublicKey: PublicKey): Application {
            if(!storagePath.exists()){
                storagePath.createDirectory()
            }

            // TODO: Change from authority to public key - they authority should come from the private store based on the private key
            val authority = Authority(authorityPublicKey)
            val keyStore = KeyStore(storagePath.resolve(config.security.keystore.name), config.security.keystore.password)
            val fileStore = LocalFileStore(storagePath.resolve("filestore"))
            val sqLiteDB = SQLiteDB(storagePath.resolve("${authority.authorityId}.db")).db

            val injector = DI {
                bindSingleton { authority }
                bindSingleton { keyStore }
                bindSingleton { fileStore }
                bindSingleton { TextExtractor() }
                bindSingleton { Signator(instance()) }
                bindSingleton { AddressBook(instance(), storagePath, config.network) }
                bindSingleton { PeerRouter(instance(), instance()) }
                bindSingleton { LuceneSearchIndex(authority.authorityId, storagePath.resolve("lucene")) }
                bindSingleton { ExposedEntityStore(instance(), instance(), instance(), instance(), sqLiteDB) }
                bindSingleton { SearchService(instance(), instance(), instance()) }
                // TODO: Add unit tests for MhtCache
                // TODO: Get cache name from config
                bindSingleton { MhtCache(storagePath.resolve("mht-cache"), instance(), instance()) }
                bindSingleton { MainReactor(instance(), instance(), instance(), instance()) }
                bindSingleton { EventBus(storagePath, config.eventBus) }
            }

            val reactor by injector.instance<Reactor>()
            val eventBus by injector.instance<EventBus>()

            eventBus.start(reactor)

            return Application(applicationPath, storagePath, config, injector)
        }
    }
}