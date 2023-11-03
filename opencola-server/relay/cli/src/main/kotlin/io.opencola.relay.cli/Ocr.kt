package io.opencola.relay.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.opencola.model.Id
import kotlin.io.path.deleteIfExists

// https://github.com/ajalt/clikt/blob/master/samples/repo/src/main/kotlin/com/github/ajalt/clikt/samples/repo/main.kt

class SetPolicy(val context: Context) : CliktCommand(name = "set") {
    val name: String by argument(help = "The name of the policy")
    val path: String by argument(help = "The path to the policy.js file")
    override fun run() = Unit
}

class GetPolicy(val context: Context) : CliktCommand(name = "get") {
    val name: String by argument(help = "The name of the policy")
    override fun run() = Unit
}

class Policy(val context: Context) : CliktCommand(name = "policy") {
    override fun run() = Unit
}

class SetUserPolicy(val context: Context) : CliktCommand(name = "set") {
    private val userId: String by argument(help = "The id of the user")
    private val name: String by argument(help = "The name of the policy")
    override fun run() {
        println("userId: $userId, name: $name")
    }
}

class GetUserPolicy(val context: Context) : CliktCommand(name = "get") {
    // val userId: String by argument(help = "The id of the user")
    override fun run() = Unit
}

class UserPolicy(val context: Context) : CliktCommand(name = "user-policy") {
    override fun run() = Unit
}

class Identity(private val context: Context) : CliktCommand(name = "identity") {
    private val delete: Boolean by option("-r", "--reset", help = "Reset identity").flag()

    override fun run() {
        if(delete) {
            context.storagePath.resolve("keystore.pks").deleteIfExists()
        } else {
            println("Id: ${Id.ofPublicKey(context.keyPair.public)}")
        }
    }
}

class Config(private val context: Context) : CliktCommand(name = "config") {
    override fun run() {
        println("storagePath: ${context.storagePath}")
    }
}

class Ocr : CliktCommand() {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    val storagePath = initStorage()
    val keyPair = getKeyPair(initStorage(), getPasswordHash())
        ?: error("KeyPair not found. You may need to delete your identity and try again.")
    val context = Context(storagePath, keyPair)

//    val client = WebSocketClient(
//        URI("ocr://relay.opencola.net"),
//        keyPair,
//        "OCR CLI")
//
//    runBlocking {
//        client.open { _, _ -> }
//        client.waitUntilOpen()
//    }

    Ocr()
        .subcommands(
            Identity(context),
            Config(context),
            Policy(context).subcommands(SetPolicy(context), GetPolicy(context)),
            UserPolicy(context).subcommands(SetUserPolicy(context), GetUserPolicy(context))
        )
        .main(args)
}
