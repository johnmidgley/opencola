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
import io.opencola.core.network.providers.relay.OCRelayNetworkProvider
import io.opencola.core.search.LuceneSearchIndex
import io.opencola.core.security.*
import io.opencola.core.storage.*
import org.jetbrains.exposed.sql.Database
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import java.io.File
import java.nio.file.Path
import java.security.KeyPair
import kotlin.io.path.*

class Application(val storagePath: Path, val config: Config, val injector: DI) {
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
        fun getOrCreateRootKeyPair(storagePath: Path, password: String): KeyPair {
            val publicKeyFile = "authority.pub" // TODO: Config?
            val authorityPubPath = storagePath.resolve(publicKeyFile)
            val keyStore = KeyStore(storagePath.resolve("keystore.pks"), password)
            val keyPair =  if (authorityPubPath.exists()) {
                val publicKey = decodePublicKey(authorityPubPath.readText())
                val privateKey = keyStore.getPrivateKey(Id.ofPublicKey(publicKey).toString())
                if(privateKey != null)
                    logger.info { "Found private key in store" }
                else
                    throw IllegalStateException("No private key found in keystore {${keyStore.path}} for public key {${publicKey}} found in $publicKeyFile")

                KeyPair(publicKey, privateKey)
            } else {
                logger.info { "Key file $publicKeyFile doesn't exist. Creating new KeyPair" }
                val keyPair = generateKeyPair()
                keyStore.addKey(Id.ofPublicKey(keyPair.public).toString(), keyPair)
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

        fun instance(storagePath: Path, config: Config, authorityKeyPair: KeyPair, password: String): Application {
            if(!storagePath.exists()){
                File(storagePath.toString()).mkdirs()
            }

            // TODO: Change from authority to public key - they authority should come from the private store based on the private key
            val defaultAddress = config.network.defaultAddress
            val authority = Authority(authorityKeyPair.public, defaultAddress, "You")

            val keyStore = KeyStore(storagePath.resolve("keystore.pks"), password)
            val fileStore = LocalFileStore(storagePath.resolve("filestore"))
            val entityStoreDB = getEntityStoreDB(authority, storagePath)

            val injector = DI {
                bindSingleton { authority }
                bindSingleton { keyStore }
                bindSingleton { fileStore }
                bindSingleton { TextExtractor() }
                bindSingleton { Signator(instance()) }
                bindSingleton { Encryptor(instance()) }
                bindSingleton { AddressBook(instance(), storagePath, instance(), config.network) }
                bindSingleton { RequestRouter(getDefaultRoutes(instance(), instance(), instance())) }
                bindSingleton { HttpNetworkProvider(instance(), instance(), instance(), instance(), config.network) }
                bindSingleton { OCRelayNetworkProvider(instance(), instance(), instance(), instance(), authorityKeyPair, config.network) }
                bindSingleton { NetworkNode(authority, instance(),instance(), instance()) }
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

            return Application(storagePath, config, injector).also { app ->
                app.inject<NetworkNode>().let { networkNode ->
                    networkNode.addProvider(app.inject<HttpNetworkProvider>())
                    networkNode.addProvider(app.inject<OCRelayNetworkProvider>())
                }
            }
        }

        fun instance(storagePath: Path, password: String, config: Config? = null) : Application {
            val appConfig = config ?: loadConfig(storagePath.resolve("opencola-server.yaml"))
            val keyPair = getOrCreateRootKeyPair(storagePath, password)
            return instance(storagePath, appConfig, keyPair, password)
        }
    }
}