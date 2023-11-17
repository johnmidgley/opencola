package io.opencola.relay.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import io.opencola.model.Id
import io.opencola.relay.common.message.v2.AdminMessage
import io.opencola.relay.common.message.v2.GetPolicyCommand
import io.opencola.relay.common.message.v2.GetPolicyResponse
import io.opencola.relay.common.message.v2.SetPolicyCommand
import io.opencola.relay.common.policy.Policy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import kotlin.io.path.deleteIfExists

// https://github.com/ajalt/clikt/blob/master/samples/repo/src/main/kotlin/com/github/ajalt/clikt/samples/repo/main.kt

inline fun <reified T> Json.tryDecodeFromString(jsonString: String): T? {
    return try {
        decodeFromString<T>(jsonString)
    } catch (e: Exception) {
        null
    }
}

fun Context.sendCommandMessage(command: AdminMessage): AdminMessage {
    return runBlocking {
        client.sendAdminMessage(command)
        withTimeout(10000) {
            responseChannel.receive()
        }
    }
}

// Example policy (with defaults left out):
// {"name":"admin","adminPolicy":{"isAdmin":true,"canEditPolicies":true,"canEditUserPolicies":true}}
// Escaped for command line use:
// {\"name\":\"admin\",\"adminPolicy\":{\"isAdmin\":true,\"canEditPolicies\":true,\"canEditUserPolicies\":true}}
class SetPolicyCliktCommand(val context: Context) : CliktCommand(name = "set") {
    val json by option(help = "The policy in json format")
    val file by option(help = "The policy in json format").file()
    override fun run() {
        if (json == null && file == null) {
            println("Must specify either --json or --file")
            return
        }

        if (json != null && file != null) {
            println("Must specify either --json or --file, not both")
            return
        }

        val jsonString = if (json != null) json else file?.readText()

        if(jsonString == null) {
            println("No policy specified")
            return
        }

        val policy = context.json.tryDecodeFromString<Policy>(jsonString)

        if(policy == null) {
            println("Invalid policy: $jsonString")
            return
        }

        val command = SetPolicyCommand(policy)
        val response = context.sendCommandMessage(command)
        println(response)
    }
}

class GetPolicyCliktCommand(private val context: Context) : CliktCommand(name = "get") {
    private val name: String? by argument(help = "The name of the policy")
    override fun run() {
        if (name == null) {
            println("name: null")
        } else {
            val response = context.sendCommandMessage(GetPolicyCommand(name!!)) as GetPolicyResponse
            println(context.json.encodeToString(response.policy))
        }
    }
}

class PolicyCliktCommand(val context: Context) : CliktCommand(name = "policy") {
    override fun run() = Unit
}

class SetUserPolicyCliktCommand(val context: Context) : CliktCommand(name = "set") {
    private val userId: String by argument(help = "The id of the user")
    private val name: String by argument(help = "The name of the policy")
    override fun run() {
        println("userId: $userId, name: $name")
    }
}

class GetUserPolicyCliktCommand(val context: Context) : CliktCommand(name = "get") {
    // val userId: String by argument(help = "The id of the user")
    override fun run() = Unit
}

class UserPolicyCliktCommand(val context: Context) : CliktCommand(name = "user-policy") {
    override fun run() = Unit
}

class IdentityCliktCommand(private val context: Context) : CliktCommand(name = "identity") {
    private val delete: Boolean by option("-r", "--reset", help = "Reset identity").flag()

    override fun run() {
        if (delete) {
            context.storagePath.resolve("keystore.pks").deleteIfExists()
        } else {
            println("Id: ${Id.ofPublicKey(context.keyPair.public)}")
        }
    }
}

class ConfigCliktCommand(private val context: Context) : CliktCommand(name = "config") {
    override fun run() {
        println("storagePath: ${context.storagePath}")
    }
}

class OcrCliktCommand : CliktCommand() {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    val storagePath = initStorage()
    val keyPair = getKeyPair(initStorage(), getPasswordHash())
        ?: error("KeyPair not found. You may need to delete your identity and try again.")

    runBlocking {
        val context = Context(storagePath, keyPair, URI("ocr://localhost"))

        context.use {
            OcrCliktCommand()
                .subcommands(
                    IdentityCliktCommand(context),
                    ConfigCliktCommand(context),
                    PolicyCliktCommand(context).subcommands(SetPolicyCliktCommand(context),
                        GetPolicyCliktCommand(context)
                    ),
                    UserPolicyCliktCommand(context).subcommands(SetUserPolicyCliktCommand(context), GetUserPolicyCliktCommand(context))
                )
                .main(args)
        }
    }
}
