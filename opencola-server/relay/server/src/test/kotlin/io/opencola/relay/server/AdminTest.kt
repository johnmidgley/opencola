package io.opencola.relay.server

import io.opencola.io.StdoutMonitor
import io.opencola.model.Id
import io.opencola.relay.ClientType
import io.opencola.relay.client.AbstractClient
import io.opencola.relay.common.message.v2.*
import io.opencola.relay.common.policy.AdminPolicy
import io.opencola.relay.common.policy.Policy
import io.opencola.relay.getClient
import io.opencola.relay.getNewServerUri
import io.opencola.security.generateKeyPair
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Test
import kotlin.test.assertEquals


class AdminTest {
    private suspend fun AbstractClient.sendCommandMessage(message: CommandMessage) {
        this.sendMessage(RelayServer.rootKeyPair.public, MessageStorageKey.none, message.toPayload())
    }

    private fun checkResponse(response: CommandMessage, sourceId: String? = null) {
        println(response)
        (response as? CommandResponse)?.let {
            assertEquals(Status.SUCCESS, it.status)
            assertEquals(State.COMPLETE, it.state)

            if(sourceId != null) {
                assertEquals(sourceId, it.id)
            }
        }
    }

    // Tests set / get of "admin" policy
    private suspend fun testSetPolicy(rootClient: AbstractClient, responseChannel: Channel<CommandMessage>) {
        val policy = Policy("admin", AdminPolicy(isAdmin = true, canEditPolicies = true, canEditUserPolicies = true))

        rootClient.sendCommandMessage(SetPolicyCommand(policy))
        withTimeout(1000) { checkResponse(responseChannel.receive()) }

        rootClient.sendCommandMessage(GetPolicyCommand("admin"))
        withTimeout(1000) {
            (responseChannel.receive() as GetPolicyResponse).let {
                assertEquals(policy, it.policy)
                println(it)
            }
        }

        rootClient.sendCommandMessage(GetPoliciesCommand())
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
        responseChannel: Channel<CommandMessage>
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
                        it.open { _, message -> responseChannel.send(CommandMessage.fromPayload(message)) }
                        it.waitUntilOpen()
                    }
                }

                StdoutMonitor().use {
                    client!!.sendCommandMessage(SetPolicyCommand(Policy("default")))
                    it.waitUntil("is not admin", 1000)
                }

                rootClient.sendCommandMessage(SetUserPolicyCommand(clientId, "admin"))
                withTimeout(1000) { checkResponse(responseChannel.receive()) }

                client!!.sendCommandMessage(SetPolicyCommand(Policy("default")))
                withTimeout(1000) { checkResponse(responseChannel.receive()) }

                rootClient.sendCommandMessage(GetUserPolicyCommand(clientId))
                withTimeout(1000) {
                    (responseChannel.receive() as GetUserPolicyResponse).let {
                        println(it)
                        assertEquals("admin", it.policy!!.name)
                    }
                }

                rootClient.sendCommandMessage(GetUserPoliciesCommand())
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
        responseChannel: Channel<CommandMessage>
    ) {
        coroutineScope {
            val toPublicKey = generateKeyPair().public
            val toId = Id.ofPublicKey(toPublicKey)

            StdoutMonitor().use {
                rootClient.sendMessage(toPublicKey, MessageStorageKey.unique(), "test".toByteArray())
                it.waitUntil("Adding message:", 1000)
                rootClient.sendMessage(toPublicKey, MessageStorageKey.unique(), "test".toByteArray())
                it.waitUntil("Adding message:", 1000)
                rootClient.sendMessage(toPublicKey, MessageStorageKey.unique(), "test".toByteArray())
                it.waitUntil("Adding message:", 1000)
            }

            println("Checking for stored message")
            server.messageStore.getMessages(toId).let { assertEquals(3, it.size) }

            RemoveUserMessagesCommand(toId).let {
                rootClient.sendCommandMessage(it)
                withTimeout(1000) { checkResponse(responseChannel.receive(), it.id) }
            }
            println("Checking for removed message")
            server.messageStore.getMessages(toId).let { assertEquals(0, it.size) }
        }
    }

    @Test
    fun testAdminCommands() {
        var server0: RelayServer? = null
        var rootClient: AbstractClient? = null

        runBlocking {
            try {
                server0 = RelayServer(getNewServerUri()).also { it.start() }
                val responseChannel = Channel<CommandMessage>()

                println("Starting client0")
                rootClient = getClient(
                    ClientType.V2,
                    "client0",
                    RelayServer.rootKeyPair,
                    relayServerUri = server0!!.address
                ).also {
                    launch {
                        it.open { _, message -> responseChannel.send(CommandMessage.fromPayload(message)) }
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