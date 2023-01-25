package io.opencola.application

import mu.KotlinLogging
import io.opencola.content.TextExtractor
import io.opencola.event.EventBus
import io.opencola.event.Reactor
import io.opencola.model.Authority
import io.opencola.model.Id
import io.opencola.model.Persona
import io.opencola.network.NetworkNode
import io.opencola.network.RequestRouter
import io.opencola.network.getDefaultRoutes
import io.opencola.network.providers.http.HttpNetworkProvider
import io.opencola.network.providers.relay.OCRelayNetworkProvider
import io.opencola.search.LuceneSearchIndex
import io.opencola.security.*
import io.opencola.storage.*
import io.opencola.security.Encryptor
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

        // TODO: Rename - not single keypair anymore. Maybe getOrCreateRootKeyPairs, but doesn't seem right.
        fun getOrCreateRootKeyPair(storagePath: Path, password: String): List<KeyPair> {
            val keyStore = KeyStore(storagePath.resolve("keystore.pks"), password)
            val aliases = keyStore.getAliases()

            val keyPair = if(aliases.isEmpty()) {
                logger.info { "Creating new KeyPair" }
                generateKeyPair().also { keyStore.addKey(Id.ofPublicKey(it.public).toString(), it) }
            } else if(aliases.size == 1) {
                keyStore.getKeyPair(aliases.first())
            } else {
                throw IllegalStateException("Multiple keys found in keystore {${keyStore.path}}")
            }

            if(keyPair == null) {
                throw IllegalStateException("Unable to get key pair from keystore {${keyStore.path}}")
            }

            return listOf(keyPair)
        }

        fun getEntityStoreDB(storagePath: Path): Database {
            return SQLiteDB(storagePath.resolve("entity-store.db")).db
        }

        fun instance(storagePath: Path, config: Config, personaKeyPairs: List<KeyPair>, password: String): Application {
            if(!storagePath.exists()){
                File(storagePath.toString()).mkdirs()
            }

            // TODO: Change from authority to public key - they authority should come from the private store based on the private key
            val defaultAddress = config.network.defaultAddress
            val personas = personaKeyPairs.map { Persona(Authority(it.public, defaultAddress, "You"), it) }

            val keyStore = KeyStore(storagePath.resolve("keystore.pks"), password)
            val fileStore = LocalFileStore(storagePath.resolve("filestore"))
            val entityStoreDB = getEntityStoreDB(storagePath)

            val injector = DI {
                bindSingleton { keyStore }
                bindSingleton { fileStore }
                bindSingleton { TextExtractor() }
                bindSingleton { Signator(instance()) }
                bindSingleton { Encryptor(instance()) }
                bindSingleton { AddressBook(storagePath, instance()) }
                bindSingleton { RequestRouter(getDefaultRoutes(instance(), instance(), instance())) }
                bindSingleton { HttpNetworkProvider(instance(), instance(), instance(), config.network) }
                bindSingleton { OCRelayNetworkProvider(instance(), instance(), instance(), instance(), config.network) }
                bindSingleton { NetworkNode(instance(),instance(), instance()) }
                bindSingleton { LuceneSearchIndex(storagePath.resolve("lucene")) }
                bindSingleton { ExposedEntityStore(entityStoreDB, instance(), instance(), instance()) }
                // TODO: Add unit tests for MhtCache
                // TODO: Get cache name from config
                bindSingleton { MhtCache(storagePath.resolve("mht-cache"), instance(), instance()) }
                bindSingleton { MainReactor(instance(), instance(), instance(), instance()) }
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
            val personaKeyPairs = getOrCreateRootKeyPair(storagePath, password)
            return instance(storagePath, appConfig, personaKeyPairs, password)
        }
    }
}