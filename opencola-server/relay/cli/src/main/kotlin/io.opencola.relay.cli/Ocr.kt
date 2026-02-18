/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.relay.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.long
import io.opencola.io.Color
import io.opencola.io.colorize
import io.opencola.model.Id
import io.opencola.relay.common.message.v2.*
import io.opencola.relay.common.policy.Policy
import io.opencola.security.generateKeyPair
import io.opencola.util.Base58
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.*

// https://github.com/ajalt/clikt/blob/master/samples/repo/src/main/kotlin/com/github/ajalt/clikt/samples/repo/main.kt

inline fun <reified T> Json.tryDecodeFromString(jsonString: String): T? {
    return try {
        decodeFromString<T>(jsonString)
    } catch (e: Exception) {
        null
    }
}

fun AdminMessage.format(): String {
    return when (this) {
        is CommandResponse -> {
            val message = message ?: ""
            val suffix = if (message.isBlank() || message.endsWith("\n")) "" else "\n"
            "${if (status != Status.SUCCESS) colorize(Color.RED, "$status: ") else ""}$message$suffix"
        }

        is ExecCommandResponse -> {
            val out = if (stdout.isBlank()) "" else "$stdout\n"
            val err = if (stderr.isBlank()) "" else "$stderr\n"
            "$out$err"
        }

        else -> this.toString()
    }
}

class ConfigCliktCommand(private val context: Context) : CliktCommand(name = "config") {
    override fun run() {
        println("storagePath: ${context.storagePath}")

        context.storagePath.resolve(configFileName).let {
            println("\n${it.readText()}")
        }
    }
}

class IdentityCliktCommand(private val context: Context) : CliktCommand(name = "identity") {
    private val delete: Boolean by option(
        "-d",
        "--delete",
        help = "Delete client identity (a new one will be created on next run)"
    ).flag()
    private val server: Boolean by option("-s", "--server", help = "Generate server identity").flag()

    override fun run() {
        if (delete && server)
            throw CliktError("Cannot specify both --delete and --server")
        if (server) {
            val keyPair = generateKeyPair()
            val id = Id.ofPublicKey(keyPair.public)
            println("Generated server identity:\n")
            println("serverId: $id")
            println("publicKeyBase58: ${Base58.encode(keyPair.public.encoded)}")
            println("privateKeyBase58: ${Base58.encode(keyPair.private.encoded)}")
        } else if (delete) {
            context.storagePath.resolve("keystore.pks").deleteIfExists()
        } else {
            println("${Id.ofPublicKey(context.keyPair.public)}")
        }
    }
}

// Example policy (with defaults left out):
// {"name":"admin","adminPolicy":{"isAdmin":true,"canEditPolicies":true,"canEditUserPolicies":true}}
// Escaped for command line use:
// {\"name\":\"admin\",\"adminPolicy\":{\"isAdmin\":true,\"canEditPolicies\":true,\"canEditUserPolicies\":true}}
class SetPolicyCliktCommand(val context: Context) : CliktCommand(name = "set") {
    private val json by option(help = "The policy in json format")
    private val file by option(help = "The policy in json format").file()
    override fun run() {
        if (json == null && file == null)
            throw CliktError("Must specify either --json or --file")

        if (json != null && file != null)
            throw CliktError("Must specify either --json or --file, not both")

        val jsonString = (if (json != null) json else file?.readText()) ?: throw CliktError("No policy specified")
        val policy =
            context.json.tryDecodeFromString<Policy>(jsonString) ?: throw CliktError("Invalid policy: $jsonString")
        val response = context.sendCommandMessage<CommandResponse>(SetPolicyCommand(policy))
        println(response.format())
    }
}

class GetPolicyCliktCommand(private val context: Context) : CliktCommand(name = "get") {
    private val name: String by argument(help = "The name of the policy")
    override fun run() {
        val response = context.sendCommandMessage<GetPolicyResponse>(GetPolicyCommand(name))
        if (response.policy == null)
            println("Policy \"$name\" does not exist")
        else
            println(context.json.encodeToString(response.policy))
    }
}

class GetAllPoliciesCliktCommand(private val context: Context) : CliktCommand(name = "ls") {
    override fun run() {
        val response = context.sendCommandMessage<GetPoliciesResponse>(GetPoliciesCommand())
        if (response.policies.isEmpty())
            println("No policies found")
        else
            response.policies.forEach { println(context.json.encodeToString(it)) }
    }
}

class RemovePolicyCliktCommand(val context: Context) : CliktCommand(name = "remove") {
    private val name: String by argument(help = "The name of the policy")

    override fun run() {
        val response = context.sendCommandMessage<CommandResponse>(RemovePolicyCommand(name))
        println(response.format())
    }
}

class PolicyCliktCommand() : CliktCommand(name = "policy") {
    override fun run() = Unit
}

class SetUserPolicyCliktCommand(val context: Context) : CliktCommand(name = "set") {
    private val userId: String by argument(help = "The id of the user")
    private val name: String by argument(help = "The name of the policy")
    override fun run() {
        val response = context.sendCommandMessage<CommandResponse>(SetUserPolicyCommand(Id.decode(userId), name))
        println(response.format())
    }
}

class GetUserPolicyCliktCommand(val context: Context) : CliktCommand(name = "get") {
    private val userId: String by argument(help = "The id of the user")
    override fun run() {
        val id = Id.tryDecode(userId) ?: throw CliktError("Invalid user id: $userId")
        val response = context.sendCommandMessage<GetUserPolicyResponse>(GetUserPolicyCommand(id))

        if (response.policy == null)
            println("User policy for \"$userId\" does not exist")
        else
            println(context.json.encodeToString(response.policy))
    }
}

class GetAllUserPoliciesCliktCommand(private val context: Context) : CliktCommand(name = "ls") {
    override fun run() {
        val response = context.sendCommandMessage<GetUserPoliciesResponse>(GetUserPoliciesCommand())
        if (response.policies.isEmpty())
            println("No user policies found")
        else
            response.policies.forEach {
                println("${it.first}\t${it.second}")
            }
    }
}

class RemoveUserPolicyCliktCommand(val context: Context) : CliktCommand(name = "remove") {
    private val userId: String by argument(help = "The id of the user")

    override fun run() {
        val id = Id.tryDecode(userId) ?: throw CliktError("Invalid user id: $userId")
        val response = context.sendCommandMessage<CommandResponse>(RemoveUserPolicyCommand(id))
        println(response.format())
    }
}

class UserPolicyCliktCommand() : CliktCommand(name = "user-policy") {
    override fun run() = Unit
}

class ConnectionsCliktCommand(private val context: Context) : CliktCommand(name = "connections") {
    private val formatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    override fun run() {
        val response = context.sendCommandMessage<GetConnectionsResponse>(GetConnectionsCommand())
        if (response.connections.isEmpty())
            println("No connections")
        else
            println(String.format("%-42s\t%-20s\t%s", "ID", "CONNECTED", "ADDRESS"))
        response.connections.forEach {
            println("${it.id}\t${formatter.format(Date(it.connectTimeMilliseconds))}\t${it.address}")
        }
    }
}

class ListMessagesCliktCommand(private val context: Context) : CliktCommand(name = "ls") {
    override fun run() {
        val response = context.sendCommandMessage<GetMessagesResponse>(GetMessagesCommand())
        if (response.messages.isEmpty())
            println("No stored messages")
        else
            response.messages.forEach { println(it) }
    }
}

class RemoveUserMessagesCliktCommand(private val context: Context) : CliktCommand(name = "remove") {
    private val userId: String? by option(help = "The id of the user")
    private val age: Long? by option(help = "Remove messages older than age in milliseconds").long()

    override fun run() {
        if (userId == null && age == null)
            throw CliktError("Must specify either --user-id or --age")

        if (userId != null && age != null)
            throw CliktError("Must specify either --user-id or --age, not both")

        val response = if (age != null) {
            context.sendCommandMessage<CommandResponse>(RemoveMessagesByAgeCommand(age!!))
        } else {
            val id = Id.tryDecode(userId!!) ?: throw CliktError("Invalid user id: $userId")
            context.sendCommandMessage<CommandResponse>(RemoveUserMessagesCommand(id))
        }

        println(response.format())
    }
}

class GetMessageUsageCliktCommand(private val context: Context) : CliktCommand(name = "usage") {
    override fun run() {
        val response = context.sendCommandMessage<GetMessageUsageResponse>(GetMessageUsageCommand())
        if (response.usages.isEmpty())
            println("No stored messages")
        response.usages.forEach {
            println("${it.to}\t${it.numMessages}\t${it.numBytes}")
        }
    }
}

class MessagesCliktCommand() : CliktCommand(name = "messages") {
    override fun run() = Unit
}

class StorageCliktCommand() : CliktCommand(name = "storage") {
    override fun run() = Unit
}

class ExecCliktCommand(private val context: Context) : CliktCommand(name = "exec") {
    private val command by argument(help = "The command to execute - must be quoted")

    override fun run() {
        val response = context.sendCommandMessage<ExecCommandResponse>(ExecCommand(".", command))
        print(response.format())
    }
}

class ShellCliktCommand(private val context: Context) : CliktCommand(name = "shell") {
    override fun run() {
        var workingDir = "."
        println("It is recommended to run the relay shell with rlwrap (https://github.com/hanslub42/rlwrap):")
        println("  alias ocr='rlwrap ocr'\n")
        println("Welcome to the relay shell (${context.config.ocr.server.uri.host})")

        while (true) {
            print("$ ")

            val command = readln()

            if (command == "exit" || command == "quit" || command == "q")
                break

            if (command.isBlank())
                continue

            val response = context.sendCommandMessage(ExecCommand(workingDir, command)) as ExecCommandResponse
            workingDir = response.workingDir
            print(response.format())
        }
    }
}

fun getFile(context: Context, remotePath: String, localPath: String?) {
    runBlocking {
        val command = GetFileCommand(remotePath)
        val localFile = File(localPath ?: Path(remotePath).name)

        if(localFile.exists())
            throw CliktError(colorize(Color.RED,"Local file already exists: $localFile"))

        context.client.sendAdminMessage(command)

        FileOutputStream(localFile).use { outputStream ->
            do {
                val response = context.responseChannel.receive()

                if(response.id != command.id) {
                    println(colorize(Color.RED,"Received response with unexpected id: ${response.id}"))
                    continue
                }

                when (response) {
                    is GetFileBlockResponse -> {
                        outputStream.write(response.block)
                        print(".")
                    }

                    is CommandResponse -> {
                        println()
                        if(response.status != Status.SUCCESS) {
                            print(response.format())
                            localFile.delete()
                        }
                        break
                    }

                    else -> {
                        error("Unexpected response: $response")
                    }
                }
            } while (true)
        }
    }
}

class GetFileCliktCommand(private val context: Context) : CliktCommand(name = "get") {
    private val remotePath by argument(help = "Path of remote file to get")
    private val localPath by argument(help = "Path where retrieved fill should be written").optional()

    override fun run() {
        getFile(context, remotePath, localPath)
    }
}

class FileCliktCommand : CliktCommand(name = "file") {
    override fun run() = Unit
}

class OcrCliktCommand : CliktCommand(name = "ocr") {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    try {
        val storagePath = initStorage()
        val config = loadConfig(storagePath.resolve(configFileName))
        val keyPair = getKeyPair(initStorage(), getPasswordHash(config))
            ?: error("KeyPair not found. You may need to delete your identity and try again.")

        runBlocking {
            val context = Context(storagePath, config, keyPair)

            context.use {
                OcrCliktCommand()
                    .subcommands(
                        ConfigCliktCommand(context),
                        IdentityCliktCommand(context),
                        PolicyCliktCommand().subcommands(
                            SetPolicyCliktCommand(context),
                            GetPolicyCliktCommand(context),
                            GetAllPoliciesCliktCommand(context),
                            RemovePolicyCliktCommand(context)
                        ),
                        UserPolicyCliktCommand().subcommands(
                            SetUserPolicyCliktCommand(context),
                            GetUserPolicyCliktCommand(context),
                            GetAllUserPoliciesCliktCommand(context),
                            RemoveUserPolicyCliktCommand(context),
                        ),
                        ConnectionsCliktCommand(context),
                        MessagesCliktCommand().subcommands(
                            ListMessagesCliktCommand(context),
                            RemoveUserMessagesCliktCommand(context),
                            GetMessageUsageCliktCommand(context)
                        ),
                        StorageCliktCommand().subcommands(
                            GetMessageUsageCliktCommand(context)
                        ),
                        ExecCliktCommand(context),
                        ShellCliktCommand(context),
                        FileCliktCommand().subcommands(
                            GetFileCliktCommand(context)
                        )
                    )
                    .main(args)
            }
        }
    } catch (e: Exception) {
        println(e.message)
    }
}
