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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertEquals


class AdminTest {
    private suspend fun AbstractClient.sendCommandMessage(message: CommandMessage) {
        this.sendMessage(RelayServer.rootKeyPair.public, MessageStorageKey.none, message.toPayload())
    }

    private fun checkResponse(response: CommandMessage) {
        println(response)
        (response as? CommandResponse)?.let {
            assertEquals(Status.SUCCESS, it.status)
            assertEquals(State.COMPLETE, it.state)
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

    private suspend fun testSetUserPolicy(server: RelayServer, rootClient: AbstractClient, responseChannel: Channel<CommandMessage>) {
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

            } finally {
                rootClient?.close()
                server0?.stop()
            }
        }
    }
}