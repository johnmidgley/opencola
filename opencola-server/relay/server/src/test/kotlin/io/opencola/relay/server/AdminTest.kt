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
    private inline fun <reified T : AdminMessage> getResponse(
        responseChannel: Channel<AdminMessage>,
        sourceId: String? = null
    ): T {
        while (true) {
            val response = runBlocking {
                withTimeout(1000) { responseChannel.receive() }.also { println(it) }
            }

            if (response is CommandResponse) {
                assertEquals(Status.SUCCESS, response.status)

                if (sourceId != null) {
                    assertEquals(sourceId, response.id)
                }

                when (response.state) {
                    State.COMPLETE -> return response as T
                    State.PENDING -> continue
                    else -> throw Exception("Unexpected state: ${response.state}")
                }
            }

            // Any response aside from a command response implies the request is complete
            return response as T
        }
    }

    // Tests set / get of "admin" policy
    private suspend fun testSetPolicy(rootClient: AbstractClient, responseChannel: Channel<AdminMessage>) {
        val policy =
            Policy("admin", AdminPolicy(isAdmin = true, canEditPolicies = true, canEditUserPolicies = true))

        rootClient.sendAdminMessage(SetPolicyCommand(policy))
        getResponse<AdminMessage>(responseChannel)

        rootClient.sendAdminMessage(GetPolicyCommand("admin"))
        assertEquals(policy, getResponse<GetPolicyResponse>(responseChannel).policy)

        rootClient.sendAdminMessage(GetPoliciesCommand())
        assertEquals(listOf(policy), getResponse<GetPoliciesResponse>(responseChannel).policies)

        val testPolicy = Policy("test")
        rootClient.sendAdminMessage(SetPolicyCommand(testPolicy))
        getResponse<AdminMessage>(responseChannel)

        rootClient.sendAdminMessage(GetPolicyCommand("test"))
        assertEquals(testPolicy, getResponse<GetPolicyResponse>(responseChannel).policy)

        rootClient.sendAdminMessage(RemovePolicyCommand("test"))
        getResponse<AdminMessage>(responseChannel)
        rootClient.sendAdminMessage(GetPolicyCommand("test"))
        assertEquals(null, getResponse<GetPolicyResponse>(responseChannel).policy)
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
                    launch { it.open { _, message -> responseChannel.send(AdminMessage.decode(message)) } }
                    it.waitUntilOpen()
                }

                println("Sending admin message from non-admin client")
                StdoutMonitor().use {
                    client!!.sendAdminMessage(SetPolicyCommand(Policy("default")))
                    it.waitUntil("is not admin", 1000)
                }

                println("Setting admin policy for non-admin client")
                rootClient.sendAdminMessage(SetUserPolicyCommand(clientId, "admin"))
                getResponse<AdminMessage>(responseChannel)

                println("Getting admin policy for  client")
                rootClient.sendAdminMessage(GetUserPolicyCommand(clientId))
                assertEquals("admin", getResponse<GetUserPolicyResponse>(responseChannel).policy!!.name)

                println("Getting admin policies")
                rootClient.sendAdminMessage(GetUserPoliciesCommand())
                assertEquals(
                    Pair(clientId, "admin"),
                    getResponse<GetUserPoliciesResponse>(responseChannel).policies.single()
                )

                println("Setting default policy")
                val defaultPolicy = Policy("default")
                client!!.sendAdminMessage(SetPolicyCommand(defaultPolicy))
                getResponse<AdminMessage>(responseChannel)

                println("Removing admin policy for client")
                rootClient.sendAdminMessage(RemoveUserPolicyCommand(clientId))
                getResponse<AdminMessage>(responseChannel)

                println("Getting policy for client")
                rootClient.sendAdminMessage(GetUserPolicyCommand(clientId))
                assertEquals(defaultPolicy, getResponse<GetUserPolicyResponse>(responseChannel).policy)
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


            rootClient.sendAdminMessage(GetMessageUsageCommand())
            getResponse<GetMessageUsageResponse>(responseChannel).usages.single().let {
                assertEquals(toId, it.to)
                assertEquals(3, it.numMessages)
            }

            rootClient.sendAdminMessage(RemoveUserMessagesCommand(toId))
            getResponse<AdminMessage>(responseChannel)

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

            rootClient.sendAdminMessage(RemoveMessagesByAgeCommand(5000))
            getResponse<AdminMessage>(responseChannel)

            val messages = server.messageStore.getMessages(toId)
            assertEquals(1, messages.size)
            assertEquals(keptMessageKey, messages[0].header.storageKey)
        }
    }

    private suspend fun testExecuteCommand(rootClient: AbstractClient, responseChannel: Channel<AdminMessage>) {
        coroutineScope {
            rootClient.sendAdminMessage(ExecCommand(listOf("pwd")))
            getResponse<AdminMessage>(responseChannel)

            rootClient.sendAdminMessage(ExecCommand(listOf("asdf")))
            val errorResponse = responseChannel.receive()
            assertEquals(Status.FAILURE, (errorResponse as CommandResponse).status)
            println(errorResponse)
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
                testExecuteCommand(rootClient!!, responseChannel)

            } finally {
                rootClient?.close()
                server0?.stop()
            }
        }
    }
}