package io.opencola.application

import mu.KotlinLogging
import io.opencola.content.TextExtractor
import io.opencola.content.TikaContentTypeDetector
import io.opencola.event.EventBus
import io.opencola.event.ExposedEventBus
import io.opencola.event.Reactor
import io.opencola.model.Attributes
import io.opencola.model.Authority
import io.opencola.model.Id
import io.opencola.network.NetworkConfig
import io.opencola.network.NetworkNode
import io.opencola.network.getDefaultRoutes
import io.opencola.network.providers.http.HttpNetworkProvider
import io.opencola.network.providers.relay.OCRelayNetworkProvider
import io.opencola.search.LuceneSearchIndex
import io.opencola.security.*
import io.opencola.security.Encryptor
import io.opencola.security.hash.Hash
import io.opencola.security.keystore.JavaKeyStore
import io.opencola.security.keystore.KeyStore
import io.opencola.storage.addressbook.*
import io.opencola.storage.addressbook.EntityStoreAddressBook.Version
import io.opencola.storage.cache.MhtCache
import io.opencola.storage.entitystore.ExposedEntityStoreV2
import io.opencola.storage.entitystore.convertExposedEntityStoreV1ToV2
import io.opencola.storage.entitystore.getSQLiteDB
import io.opencola.storage.filestore.LocalContentBasedFileStore
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

    inline fun <reified T : Any> inject(): T {
        val instance by injector.instance<T>()
        return instance
    }

    fun getPersonas(): List<PersonaAddressBookEntry> {
        return inject<AddressBook>().getEntries().filterIsInstance<PersonaAddressBookEntry>()
    }

    companion object Global {
        // TODO: Remove - create loggers by component / namespace
        val logger = KotlinLogging.logger("opencola.init")
        private const val addressBookName = "address-book"
        private const val entityStoreName = "entity-store"
        private val migrationMessage = """
Your data has been migrated from v1 storage to v2 storage. When you are confident that everything is
working properly, you can delete entity-store.db and address-book.db.
""".trimIndent()

        // TODO: Rename - not single keypair anymore. Maybe getOrCreateRootKeyPairs, but doesn't seem right.
        fun getOrCreateRootKeyPair(storagePath: Path, passwordHash: Hash): List<KeyPair> {
            val keyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), passwordHash)

            if (keyStore.getAliases().isEmpty()) {
                generateKeyPair().also { keyStore.addKeyPair(Id.ofPublicKey(it.public).toString(), it) }
            }

            val keyPairs = keyStore.getAliases().mapNotNull { keyStore.getKeyPair(it) }

            if (keyPairs.isEmpty()) {
                throw IllegalStateException("No key pairs found in {${keyStore.path}}")
            }

            return keyPairs
        }

        private fun initEventBus(injector: DI) {
            //  TODO: Probably don't need reactor in the injector
            val reactor by injector.instance<Reactor>()
            val eventBus by injector.instance<EventBus>()

            eventBus.setReactor(reactor)
        }

        private fun initAddressBook(injector: DI, personaKeyPairs: List<KeyPair>, networkConfig: NetworkConfig) {
            val addressBook by injector.instance<AddressBook>()
            val personas = personaKeyPairs.map {
                val authority = Authority(it.public, networkConfig.defaultAddress, "You", tags = listOf("active"))
                PersonaAddressBookEntry(authority, it)
            }

            personas.forEach {
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

        private fun migrateEntityStoreV1ToV2(storagePath: Path, keyStore: KeyStore) {
            if (!storagePath.resolve("$addressBookName.db").exists())
                return

            val addressBook = EntityStoreAddressBook(Version.V1, AddressBookConfig(), storagePath, keyStore)
            val signator = Signator(keyStore)
            var migrationOccurred = false

            listOf(addressBookName, entityStoreName).forEach { name ->
                if (!storagePath.resolve(name).isDirectory()) {
                    convertExposedEntityStoreV1ToV2(
                        addressBook,
                        signator,
                        name,
                        storagePath,
                        storagePath.resolve(name),
                    )
                    migrationOccurred = true
                }
            }

            if(migrationOccurred)
                storagePath.resolve("migration-README.txt").writeText(migrationMessage)
        }

        // TODO: Should probably pass in keyStore vs. personaKeyPairs. The keyStore should be able to get the personaKeyPairs
        fun instance(storagePath: Path, config: Config, personaKeyPairs: List<KeyPair>, passwordHash: Hash): Application {
            if (!storagePath.exists()) {
                File(storagePath.toString()).mkdirs()
            }

            val keyStore = JavaKeyStore(storagePath.resolve("keystore.pks"), passwordHash)
            val fileStore = LocalContentBasedFileStore(storagePath.resolve("filestore"))

            migrateEntityStoreV1ToV2(storagePath, keyStore)

            val injector = DI {
                bindSingleton { keyStore }
                bindSingleton { fileStore }
                bindSingleton { TextExtractor() }
                bindSingleton { TikaContentTypeDetector() }
                bindSingleton { Signator(instance()) }
                bindSingleton { Encryptor(instance()) }
                bindSingleton {
                    EntityStoreAddressBook(
                        Version.V2,
                        config.addressBook,
                        storagePath.resolve(addressBookName),
                        instance()
                    )
                }
                bindSingleton { getDefaultRoutes(this) }
                bindSingleton { HttpNetworkProvider(instance(), instance(), config.network) }
                bindSingleton { OCRelayNetworkProvider(instance(), instance(), config.network) }
                bindSingleton { NetworkNode(config.network, instance(), instance(), instance()) }
                bindSingleton { LuceneSearchIndex(storagePath.resolve("lucene")) }
                bindSingleton {
                    ExposedEntityStoreV2(
                        entityStoreName,
                        storagePath.resolve(entityStoreName),
                        ::getSQLiteDB,
                        Attributes.get(),
                        instance(),
                        instance(),
                        instance()
                    )
                }
                // TODO: Add unit tests for MhtCache
                // TODO: Get cache name from config
                bindSingleton { MhtCache(storagePath.resolve("mht-cache"), instance(), instance()) }
                bindSingleton { MainReactor(config.network, instance(), instance(), instance(), instance(), instance()) }
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

        fun instance(storagePath: Path, passwordHash: Hash, config: Config? = null): Application {
            val appConfig = config ?: loadConfig(storagePath.resolve("opencola-server.yaml"))
            val personaKeyPairs = getOrCreateRootKeyPair(storagePath, passwordHash)
            return instance(storagePath, appConfig, personaKeyPairs, passwordHash)
        }
    }

    fun open(waitUntilReady: Boolean = false) {
        inject<EventBus>().start()
        inject<NetworkNode>().start(waitUntilReady)
    }

    override fun close() {
        // TODO: Make these Closeable
        inject<NetworkNode>().stop()
        inject<EventBus>().stop()
    }
}