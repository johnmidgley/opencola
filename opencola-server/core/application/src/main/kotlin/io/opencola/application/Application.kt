package io.opencola.application

import mu.KotlinLogging
import io.opencola.content.TextExtractor
import io.opencola.event.EventBus
import io.opencola.event.ExposedEventBus
import io.opencola.event.Reactor
import io.opencola.model.Authority
import io.opencola.model.Id
import io.opencola.network.NetworkConfig
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
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import java.security.KeyPair
import kotlin.io.path.*

class Application(val storagePath: Path, val config: Config, val injector: DI) : Closeable {
    val logger = KotlinLogging.logger("opencola.${config.name}")

    inline fun <reified T : Any> inject() : T {
        val instance by injector.instance<T>()
        return instance
    }

    fun getPersonas(): List<PersonaAddressBookEntry> {
        return inject<AddressBook>().getEntries().filterIsInstance<PersonaAddressBookEntry>()
    }

    companion object Global {
        // TODO: Remove - create loggers by component / namespace
        val logger = KotlinLogging.logger("opencola.init")

        // TODO: Rename - not single keypair anymore. Maybe getOrCreateRootKeyPairs, but doesn't seem right.
        fun getOrCreateRootKeyPair(storagePath: Path, password: String): List<KeyPair> {
            val keyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), password)

            if(keyStore.getAliases().isEmpty()) {
                generateKeyPair().also { keyStore.addKeyPair(Id.ofPublicKey(it.public).toString(), it) }
            }

            val keyPairs = keyStore.getAliases().mapNotNull { keyStore.getKeyPair(it) }

            if(keyPairs.isEmpty()) {
                throw IllegalStateException("No key pairs found in {${keyStore.path}}")
            }

            return keyPairs
        }

        fun getEntityStoreDB(storagePath: Path): Database {
            return SQLiteDB(storagePath.resolve("entity-store.db")).db
        }

        private fun initEventBus(injector: DI) {
            //  TODO: Probably don't need reactor in the injector
            val reactor by injector.instance<Reactor>()
            val eventBus by injector.instance<EventBus>()

            eventBus.start(reactor)
        }

        private fun initAddressBook(injector: DI, personaKeyPairs: List<KeyPair>, networkConfig: NetworkConfig) {
            val addressBook by injector.instance<AddressBook>()
            val personas = personaKeyPairs.map {
                val authority = Authority(it.public, networkConfig.defaultAddress, "You", tags = setOf("active"))
                PersonaAddressBookEntry(authority, it)
            }

            personas.forEach{
                val addressBookPersona = addressBook.getEntry(it.personaId, it.entityId) ?: addressBook.updateEntry(it)

                if (addressBookPersona.address.toString().isBlank()) {
                    // Fallback to local server address.
                    val updatedEntry = AddressBookEntry(
                        addressBookPersona.personaId,
                        addressBookPersona.entityId,
                        addressBookPersona.name,
                        addressBookPersona.publicKey,
                        networkConfig.defaultAddress,
                        addressBookPersona.imageUri,
                        addressBookPersona.isActive
                    )
                    addressBook.updateEntry(updatedEntry)
                }
            }
        }

        // TODO: Should probably pass in keyStore vs. personaKeyPairs. The keyStore should be able to get the personaKeyPairs
        fun instance(storagePath: Path, config: Config, personaKeyPairs: List<KeyPair>, password: String): Application {
            if(!storagePath.exists()) {
                File(storagePath.toString()).mkdirs()
            }

            val keyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), password)
            val fileStore = LocalFileStore(storagePath.resolve("filestore"))
            val entityStoreDB = getEntityStoreDB(storagePath)

            val injector = DI {
                bindSingleton { keyStore }
                bindSingleton { fileStore }
                bindSingleton { TextExtractor() }
                bindSingleton { Signator(instance()) }
                bindSingleton { Encryptor(instance()) }
                bindSingleton { EntityStoreAddressBook(storagePath, instance()) }
                bindSingleton { RequestRouter(instance(), getDefaultRoutes(instance(), instance(), instance())) }
                bindSingleton { HttpNetworkProvider(instance(), instance(), instance(), config.network) }
                bindSingleton { OCRelayNetworkProvider(instance(), instance(), instance(), config.network) }
                bindSingleton { NetworkNode(config.network, instance(),instance(), instance()) }
                bindSingleton { LuceneSearchIndex(storagePath.resolve("lucene")) }
                bindSingleton { ExposedEntityStore(entityStoreDB, instance(), instance(), instance()) }
                // TODO: Add unit tests for MhtCache
                // TODO: Get cache name from config
                bindSingleton { MhtCache(storagePath.resolve("mht-cache"), instance(), instance()) }
                bindSingleton { MainReactor(config.network, instance(), instance(), instance(), instance()) }
                bindSingleton { ExposedEventBus(storagePath, config.eventBus) }
            }

            initEventBus(injector)
            initAddressBook(injector, personaKeyPairs, config.network)

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

    override fun close() {
        inject<NetworkNode>().stop()
        inject<EventBus>().stop()
    }
}