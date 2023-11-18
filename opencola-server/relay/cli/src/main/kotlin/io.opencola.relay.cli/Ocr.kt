package io.opencola.relay.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import io.opencola.model.Id
import io.opencola.relay.common.message.v2.*
import io.opencola.relay.common.policy.Policy
import kotlinx.coroutines.runBlocking
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

fun AdminMessage.format(): String {
    return when (this) {
        is CommandResponse -> "${if (status != Status.SUCCESS) "$status: " else ""}$message"
        else -> this.toString()
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
        val response = context.sendCommandMessage(SetPolicyCommand(policy))
        println(response.format())
    }
}

class GetPolicyCliktCommand(private val context: Context) : CliktCommand(name = "get") {
    private val name: String by argument(help = "The name of the policy")
    override fun run() {
        val response = context.sendCommandMessage(GetPolicyCommand(name))

        if (response is GetPolicyResponse) {
            if (response.policy == null)
                println("Policy \"$name\" does not exist")
            else
                println(context.json.encodeToString(response.policy))
        } else {
            println(response.format())
        }
    }
}

class GetAllPoliciesCliktCommand(private val context: Context) : CliktCommand(name = "get-all") {
    override fun run() {
        val response = context.sendCommandMessage(GetPoliciesCommand())

        if (response is GetPoliciesResponse) {
            if (response.policies.isEmpty())
                println("No policies found")
            else
                response.policies.forEach { println(context.json.encodeToString(it)) }
        } else
            println(response.format())
    }
}

class RemovePolicyCliktCommand(val context: Context) : CliktCommand(name = "remove") {
    private val name: String by argument(help = "The name of the policy")

    override fun run() {
        val response = context.sendCommandMessage(RemovePolicyCommand(name))
        println(response.format())
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

class OcrCliktCommand : CliktCommand(name = "ocr") {
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
                    PolicyCliktCommand(context).subcommands(
                        SetPolicyCliktCommand(context),
                        GetPolicyCliktCommand(context),
                        GetAllPoliciesCliktCommand(context),
                        RemovePolicyCliktCommand(context)
                    ),
                    UserPolicyCliktCommand(context).subcommands(
                        SetUserPolicyCliktCommand(context),
                        GetUserPolicyCliktCommand(context)
                    )
                )
                .main(args)
        }
    }
}
