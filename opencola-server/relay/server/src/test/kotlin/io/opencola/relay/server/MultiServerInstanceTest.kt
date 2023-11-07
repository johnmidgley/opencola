package io.opencola.relay.server

import io.opencola.application.TestApplication
import io.opencola.io.StdoutMonitor
import io.opencola.model.Id
import io.opencola.relay.ClientType
import io.opencola.relay.client.AbstractClient
import io.opencola.relay.common.connection.ConnectionsDB
import io.opencola.relay.common.connection.ExposedConnectionDirectory
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.message.v2.store.ExposedMessageStore
import io.opencola.relay.common.policy.MemoryPolicyStore
import io.opencola.relay.getClient
import io.opencola.relay.getNewServerUri
import io.opencola.security.generateKeyPair
import io.opencola.storage.filestore.ContentAddressedFileStore
import io.opencola.storage.filestore.FileSystemContentAddressedFileStore
import io.opencola.storage.newSQLiteDB
import io.opencola.util.append
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MultiServerInstanceTest {
    // TODO: Move to common test library
    private fun newFileStore(): ContentAddressedFileStore {
        val fileStoreDirectory = TestApplication.getTmpDirectory("-filestore")
        return FileSystemContentAddressedFileStore(fileStoreDirectory)
    }

    @Test
    // Starting a relay server takes time, so to speed up testing, we do a lot in this test
    fun testMultiServerSend() {
        println("testMultiServerSend")

        runBlocking {
            var server0: RelayServer? = null
            var server1: RelayServer? = null
            var client0: AbstractClient? = null
            var client1: AbstractClient? = null
            var client2: AbstractClient? = null

            try {
                val fileStore = newFileStore()
                val sqlLiteDB = newSQLiteDB("testMultiServerSend")

                println("Starting server0")
                val server0Address = getNewServerUri()
                val policyStore = MemoryPolicyStore(RelayServer.config.security.rootId)
                val server0ConnectionDirectory = ExposedConnectionDirectory(sqlLiteDB, server0Address)
                val server0MessageStore = ExposedMessageStore(sqlLiteDB, fileStore)
                server0 = RelayServer(server0Address, policyStore, server0ConnectionDirectory, server0MessageStore).also { it.start() }

                println("Starting server1")
                val server1Address = getNewServerUri()
                val server1ConnectionDirectory = ExposedConnectionDirectory(sqlLiteDB, server1Address)
                val server1MessageStore = ExposedMessageStore(sqlLiteDB, fileStore)
                server1 = RelayServer(server1Address, policyStore, server1ConnectionDirectory, server1MessageStore).also { it.start() }

                val results = Channel<ByteArray>()

                println("Starting client0")
                client0 = getClient(ClientType.V2, "client0", relayServerUri = server0Address).also {
                    launch {
                        it.open { _, message ->
                            results.send(message)
                            "".toByteArray()
                        }
                    }
                    it.waitUntilOpen()
                }

                println("Starting client1")
                client1 = getClient(ClientType.V2, "client1", relayServerUri = server1Address)
                    .also {
                        launch {
                            it.open { from, message ->
                                assertEquals(client0.publicKey, from)
                                val response = message.append(" client1".toByteArray())
                                it.sendMessage(from, MessageStorageKey.unique(), response)
                            }
                        }

                        it.waitUntilOpen()
                    }

                println("Sending message from client0 to client1")
                client0.sendMessage(client1.publicKey, MessageStorageKey.unique(), "hello".toByteArray())
                withTimeout(3000) { assertEquals("hello client1", String(results.receive())) }

                println("Sending message to unconnected client2")
                val client2KeyPair = generateKeyPair()
                client0.sendMessage(client2KeyPair.public, MessageStorageKey.unique(), "hello".toByteArray())

                println("Starting client2")
                client2 = getClient(ClientType.V2, "client2", relayServerUri = server1Address, keyPair = client2KeyPair)
                    .also {
                        launch {
                            it.open { from, message ->
                                assertEquals(client0.publicKey, from)
                                val response = message.append(" client2".toByteArray())
                                it.sendMessage(from, MessageStorageKey.unique(), response)
                            }
                        }

                        it.waitUntilOpen()
                    }

                withTimeout(3000) { assertEquals("hello client2", String(results.receive())) }

                val connectionsDB = ConnectionsDB(sqlLiteDB)
                val orphanedKeyPair = generateKeyPair()
                val orphanedId = Id.ofPublicKey(orphanedKeyPair.public)
                val unavailableServerUri = getNewServerUri()
                // TODO: Add test for aging out data
                connectionsDB.upsertConnection(orphanedId, unavailableServerUri, 0)
                assertNotNull(connectionsDB.getConnection(orphanedId))

                println("Sending message to orphaned client: $orphanedId")
                StdoutMonitor().use {
                    client0.sendMessage(orphanedKeyPair.public, MessageStorageKey.unique(), "hello".toByteArray())
                    it.waitUntil("Unable to connect to server $unavailableServerUri", 13000)
                }

                assertNull(connectionsDB.getConnection(orphanedId))

                // TODO: Test bad connection entry that points to live server, but no active connection to receive
                connectionsDB.upsertConnection(orphanedId, server1Address, 0)
                assertNotNull(connectionsDB.getConnection(orphanedId))
                StdoutMonitor().use {
                    client0.sendMessage(orphanedKeyPair.public, MessageStorageKey.unique(), "hello".toByteArray())
                    it.waitUntil("No connection for id $orphanedId", 3000)
                }

                assertNull(connectionsDB.getConnection(orphanedId))
            } finally {
                println("Closing Resources")
                client0?.close()
                client1?.close()
                client2?.close()
                server0?.stop()
                server1?.stop()
            }
        }
    }
}