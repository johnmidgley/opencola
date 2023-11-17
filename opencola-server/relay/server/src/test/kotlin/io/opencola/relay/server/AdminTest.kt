package io.opencola.relay.server

import io.opencola.io.StdoutMonitor
import io.opencola.model.Id
import io.opencola.relay.ClientType
import io.opencola.relay.client.AbstractClient
import io.opencola.relay.common.message.v2.*
import io.opencola.relay.common.message.v2.store.MessagesDB
import io.opencola.relay.common.policy.AdminPolicy
import io.opencola.relay.common.policy.Policy
import io.opencola.relay.getClient
import io.opencola.relay.getNewServerUri
import io.opencola.security.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertEquals


class AdminTest {
    private suspend fun AbstractClient.sendAdminMessage(message: AdminMessage) {
        val controlMessage = ControlMessage(ControlMessageType.ADMIN, message.encode())
        this.sendMessage(RelayServer.keyPair.public, MessageStorageKey.none, controlMessage.encodeProto())
    }

    private fun checkResponse(response: AdminMessage, sourceId: String? = null): AdminMessage {
        println(response)
        (response as? CommandResponse)?.let {
            assertEquals(Status.SUCCESS, it.status)
            assertEquals(State.COMPLETE, it.state)

            if (sourceId != null) {
                assertEquals(sourceId, it.id)
            }
        }

        return response
    }

    // Tests set / get of "admin" policy
    private suspend fun testSetPolicy(rootClient: AbstractClient, responseChannel: Channel<AdminMessage>) {
        val policy = Policy("admin", AdminPolicy(isAdmin = true, canEditPolicies = true, canEditUserPolicies = true))

        rootClient.sendAdminMessage(SetPolicyCommand(policy))
        withTimeout(1000) { checkResponse(responseChannel.receive()) }

        rootClient.sendAdminMessage(GetPolicyCommand("admin"))
        withTimeout(1000) {
            (responseChannel.receive() as GetPolicyResponse).let {
                assertEquals(policy, it.policy)
                println(it)
            }
        }

        rootClient.sendAdminMessage(GetPoliciesCommand())
        withTimeout(1000) {
            (responseChannel.receive() as GetPoliciesResponse).let {
                assertEquals(listOf(policy), it.policies)
                println(it)
            }
        }
    }

    private suspend fun testSetUserPolicy(
        server: RelayServer,
        rootClient: AbstractClient,
        responseChannel: Channel<AdminMessage>
    ) {
        var client: AbstractClient? = null

        coroutineScope {
            try {
                println("Starting non-admin client")
                val clientKeyPair = generateKeyPair()
                val clientId = Id.ofPublicKey(clientKeyPair.public)
                client = getClient(
                    ClientType.V2,
                    "client",
                    clientKeyPair,
                    relayServerUri = server.address
                ).also {
                    launch {
                        it.open { _, message -> responseChannel.send(AdminMessage.decode(message)) }
                        it.waitUntilOpen()
                    }
                }

                StdoutMonitor().use {
                    client!!.sendAdminMessage(SetPolicyCommand(Policy("default")))
                    it.waitUntil("is not admin", 1000)
                }

                rootClient.sendAdminMessage(SetUserPolicyCommand(clientId, "admin"))
                withTimeout(1000) { checkResponse(responseChannel.receive()) }

                client!!.sendAdminMessage(SetPolicyCommand(Policy("default")))
                withTimeout(1000) { checkResponse(responseChannel.receive()) }

                rootClient.sendAdminMessage(GetUserPolicyCommand(clientId))
                withTimeout(1000) {
                    (responseChannel.receive() as GetUserPolicyResponse).let {
                        println(it)
                        assertEquals("admin", it.policy!!.name)
                    }
                }

                rootClient.sendAdminMessage(GetUserPoliciesCommand())
                withTimeout(1000) {
                    (responseChannel.receive() as GetUserPoliciesResponse).let {
                        println(it)
                        assertEquals(Pair(clientId, "admin"), it.policies.single())
                    }
                }
            } finally {
                client?.close()
            }
        }
    }

    private suspend fun testManageMessages(
        server: RelayServer,
        rootClient: AbstractClient,
        responseChannel: Channel<AdminMessage>
    ) {
        coroutineScope {
            val toPublicKey = generateKeyPair().public
            val toId = Id.ofPublicKey(toPublicKey)

            StdoutMonitor().use {
                rootClient.sendMessage(toPublicKey, MessageStorageKey.unique(), "test".toByteArray())
                it.waitUntil("Added message:", 1000)
                rootClient.sendMessage(toPublicKey, MessageStorageKey.unique(), "test".toByteArray())
                it.waitUntil("Added message:", 1000)
                rootClient.sendMessage(toPublicKey, MessageStorageKey.unique(), "test".toByteArray())
                it.waitUntil("Added message:", 1000)
            }

            println("Checking for stored message")
            server.messageStore.getMessages(toId).let { assertEquals(3, it.size) }

            GetMessageUsageCommand().let {
                rootClient.sendAdminMessage(it)
                withTimeout(1000) {
                    val response = checkResponse(responseChannel.receive(), it.id) as GetMessageUsageResponse
                    val usage = response.usages.single()
                    assertEquals(toId, usage.to)
                    assertEquals(3, usage.numMessages)
                }
            }

            RemoveUserMessagesCommand(toId).let {
                rootClient.sendAdminMessage(it)
                withTimeout(1000) { checkResponse(responseChannel.receive(), it.id) }
            }
            println("Checking for removed message")
            server.messageStore.getMessages(toId).let { assertEquals(0, it.size) }

            val messageDB = MessagesDB(server.db)
            val secretKey = EncryptedBytes(
                EncryptionTransformation.NONE,
                EncryptionParameters(EncryptionParameters.Type.NONE, "".toByteArray()),
                "secretKey".toByteArray()
            )

            val data = SignedBytes(Signature.none, "data".toByteArray()).encodeProto()
            val dataId = server.contentAddressedFileStore.write(data)
            val keptMessageKey = MessageStorageKey.of("kept")
            messageDB.insertMessage(
                Id.new(),
                toId,
                keptMessageKey,
                secretKey,
                dataId,
                10,
                System.currentTimeMillis()
            )
            messageDB.insertMessage(
                Id.new(),
                toId,
                MessageStorageKey.unique(),
                secretKey,
                dataId,
                10,
                System.currentTimeMillis() - 1000 * 60
            )

            assertEquals(2, server.messageStore.getMessages(toId).size)
            RemoveMessagesByAgeCommand(5000).let {
                rootClient.sendAdminMessage(it)

                do {
                    val response =  withTimeout(1000) { responseChannel.receive() } as CommandResponse
                    println(response.message)
                } while (response.state != State.COMPLETE)
            }

            val messages = server.messageStore.getMessages(toId)
            assertEquals(1, messages.size)
            assertEquals(keptMessageKey, messages[0].header.storageKey)
        }
    }

    @Test
    fun testAdminCommands() {
        var server0: RelayServer? = null
        var rootClient: AbstractClient? = null

        runBlocking {
            try {
                server0 = RelayServer(getNewServerUri()).also { it.start() }
                val responseChannel = Channel<AdminMessage>()

                println("Starting client0")
                rootClient = getClient(
                    ClientType.V2,
                    "client0",
                    RelayServer.rootKeyPair,
                    relayServerUri = server0!!.address
                ).also {
                    launch {
                        it.open { _, message -> responseChannel.send(AdminMessage.decode(message)) }
                    }
                    it.waitUntilOpen()
                }

                testSetPolicy(rootClient!!, responseChannel) // Sets admin policy
                testSetUserPolicy(server0!!, rootClient!!, responseChannel)
                testManageMessages(server0!!, rootClient!!, responseChannel)

            } finally {
                rootClient?.close()
                server0?.stop()
            }
        }
    }
}