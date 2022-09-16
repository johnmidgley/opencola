package io.opencola.core.config

import mu.KotlinLogging
import io.opencola.core.content.TextExtractor
import io.opencola.core.event.EventBus
import io.opencola.core.event.MainReactor
import io.opencola.core.event.Reactor
import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import io.opencola.core.network.NetworkNode
import io.opencola.core.network.RequestRouter
import io.opencola.core.network.getDefaultRoutes
import io.opencola.core.network.providers.http.HttpNetworkProvider
import io.opencola.core.search.LuceneSearchIndex
import io.opencola.core.security.*
import io.opencola.core.storage.*
import org.jetbrains.exposed.sql.Database
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.security.KeyPair
import kotlin.io.path.*

class Application(val applicationPath: Path, val storagePath: Path, val config: Config, val injector: DI) {
    val logger = KotlinLogging.logger("opencola.${config.name}")

    inline fun <reified T : Any> inject() : T {
        val instance by injector.instance<T>()
        return instance
    }

    companion object Global {
        // TODO: Remove - create loggers by component / namespace
        val logger = KotlinLogging.logger("opencola.init")

        // TODO: pub key should come from private store, not authority.pub, and multiple authorities (personas) should be allowed
        // TODO: Move to Identity Service
        fun getOrCreateRootKeyPair(storagePath: Path, securityConfig: SecurityConfig): KeyPair {
            val publicKeyFile = "authority.pub" // TODO: Config?
            val authorityPubPath = storagePath.resolve(publicKeyFile)
            val keyStore = KeyStore(storagePath.resolve(securityConfig.keystore.name), securityConfig.keystore.password)
            val keyPair =  if (authorityPubPath.exists()) {
                val publicKey = decodePublicKey(authorityPubPath.readText())
                val privateKey = keyStore.getPrivateKey(Id.ofPublicKey(publicKey))
                if(privateKey != null)
                    logger.info { "Found private key in store" }
                else
                    throw IllegalStateException("No private key found in keystore {${keyStore.path}} for public key {${publicKey}} found in $publicKeyFile")

                KeyPair(publicKey, privateKey)
            } else {
                logger.info { "Key file $publicKeyFile doesn't exist. Creating new KeyPair" }
                val keyPair = generateKeyPair()
                keyStore.addKey(Id.ofPublicKey(keyPair.public), keyPair)
                authorityPubPath.writeText(keyPair.public.encode())
                keyPair
            }

            return keyPair
        }

        fun getEntityStoreDB(authority: Authority, storagePath: Path): Database {
            val path = storagePath.resolve("entity-store.db")

            if(!path.exists()){
                val legacyPath = storagePath.resolve("${authority.authorityId.legacyEncode()}.db")
                if(legacyPath.exists()){
                    logger.warn { "Moving legacy database." }
                    legacyPath.moveTo(path)
                }
            }

            return SQLiteDB(path).db
        }

        fun instance(applicationPath: Path, storagePath: Path, config: Config, authorityKeyPair: KeyPair): Application {
            if(!storagePath.exists()){
                File(storagePath.toString()).mkdirs()
            }

            // TODO: Change from authority to public key - they authority should come from the private store based on the private key
            val authority = Authority(authorityKeyPair.public, URI("http://${config.server.host}:${config.server.port}"), "You")
            val keyStore = KeyStore(storagePath.resolve(config.security.keystore.name), config.security.keystore.password)
            val fileStore = LocalFileStore(storagePath.resolve("filestore"))
            val entityStoreDB = getEntityStoreDB(authority, storagePath)

            val injector = DI {
                bindSingleton { authority }
                bindSingleton { keyStore }
                bindSingleton { fileStore }
                bindSingleton { TextExtractor() }
                bindSingleton { Signator(instance()) }
                bindSingleton { Encryptor(instance()) }
                bindSingleton { AddressBook(instance(), storagePath, instance(), config.server, config.network) }
                bindSingleton { RequestRouter(getDefaultRoutes(instance(), instance(), instance())) }
                bindSingleton { HttpNetworkProvider(config.server, instance(), instance()) }
                bindSingleton { NetworkNode(config.network, storagePath.resolve("network"), authority.authorityId, instance(),instance(), instance(), instance()) }
                bindSingleton { LuceneSearchIndex(authority.authorityId, storagePath.resolve("lucene")) }
                bindSingleton { ExposedEntityStore(entityStoreDB, instance(), instance(), instance(), instance()) }
                // TODO: Add unit tests for MhtCache
                // TODO: Get cache name from config
                bindSingleton { MhtCache(storagePath.resolve("mht-cache"), instance(), instance()) }
                bindSingleton { MainReactor(instance(), instance(), instance(), instance(), instance()) }
                bindSingleton { EventBus(storagePath, config.eventBus) }
            }

            val reactor by injector.instance<Reactor>()
            val eventBus by injector.instance<EventBus>()

            eventBus.start(reactor)

            return Application(applicationPath, storagePath, config, injector).also {
                it.inject<NetworkNode>().setProvider("http", it.inject<HttpNetworkProvider>())
            }
        }

        fun instance(applicationPath: Path, storagePath: Path, config: Config? = null) : Application {
            val appConfig = config ?: loadConfig(storagePath.resolve("opencola-server.yaml"))
            val keyPair = getOrCreateRootKeyPair(storagePath, appConfig.security)
            return instance(applicationPath, storagePath, appConfig, keyPair)
        }
    }
}