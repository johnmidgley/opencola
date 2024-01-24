/*
 * Copyright 2024 OpenCola
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

package io.opencola.cli

import io.opencola.model.Id
import io.opencola.security.hash.Hash
import io.opencola.security.hash.Sha256Hash
import io.opencola.storage.getStoragePath
import kotlinx.cli.*

@ExperimentalCli
class SearchCommand: Subcommand("search", "Perform search operations") {
    val rebuild by option(ArgType.Boolean, shortName = "r", description = "Rebuild index from entity store")
    val compare by option(ArgType.Boolean, shortName = "c", description = "Compare search index with entity store")
    val patch by option(ArgType.Boolean, shortName = "p", description = "Patch search index with entity store")
    val optimize by option(ArgType.Boolean, shortName = "o", description = "Optimize search index")
    val query by option(ArgType.String, shortName = "q", description = "Query the search index")

    override fun execute() { }
}

@OptIn(ExperimentalCli::class)
class KeyStoreCommand(): Subcommand("keystore", "Manage key store") {
    val list by option(ArgType.Boolean, shortName = "l", description = "List key store entries")
    val change by option(ArgType.String, shortName = "c", description = "Change key store password")
    override fun execute() { }
}

@OptIn(ExperimentalCli::class)
class EntityCommand(): Subcommand("entity", "Manage entities") {
    val cat by option(ArgType.String, shortName = "c", description = "Display entity contents")
    val ls by option(ArgType.Boolean, shortName = "l", description = "List entities")
    val rebuild by option(ArgType.Boolean, shortName = "r", description = "Rebuild entity store from transactions")
    val cmp by option(ArgType.String, shortName = "p", description = "Compare entity stores")
    override fun execute() { }
}

@OptIn(ExperimentalCli::class)
class TransactionsCommand(): Subcommand("transactions", "Manage entities") {
    val grep by option(ArgType.String, shortName = "g", description = "Grep transactions")
    override fun execute() { }
}

// TODO: Password should be char array for security
fun readPassword() : String {
    val console = System.console()

    print("Password: ")

    return if(console != null) {
        val password = console.readPassword()
        String(password)
    } else {
        readln()
    }
}

fun parseEntityIdString(entityIdString: String): Pair<Set<Id>, Set<Id>> {
    val splits = entityIdString.split(":")
    val authoritySpecified = splits.size == 2
    val authorityIds = if (authoritySpecified) setOf(Id.decode(splits[0])) else emptySet()
    val entityIds = if (authoritySpecified) setOf(Id.decode(splits[1])) else setOf(Id.decode(entityIdString))
    return Pair(authorityIds, entityIds)
}

@ExperimentalCli
// TODO: Kotlin CLI is obsolete - try https://github.com/ajalt/clikt
fun main(args: Array<String>) {
    val parser = ArgParser("oc", strictSubcommandOptionsOrder = true)
    val path by parser.option(ArgType.String, shortName = "s", description = "Storage path").default("")
    val password by parser.option(ArgType.String, shortName = "p", description = "Password").default("")

    val searchCommand = SearchCommand()
    val keyStoreCommand = KeyStoreCommand()
    val entityCommand = EntityCommand()
    val transactionsCommand = TransactionsCommand()
    parser.subcommands(searchCommand, keyStoreCommand, entityCommand, transactionsCommand)

    val parserResult = parser.parse(args)
    val actualStoragePath = getStoragePath(path)
    println("Storage path: $actualStoragePath")

    var actualPassword: Hash? = null
    val getPassword = {
        if(actualPassword == null) {
            actualPassword = Sha256Hash.ofString(password.ifEmpty { readPassword().also { println() } })
        }
        actualPassword!!
    }

    when(parserResult.commandName) {
        "search" -> search(actualStoragePath, searchCommand)
        "keystore" -> keystore(actualStoragePath, keyStoreCommand, getPassword)
        "entity" -> entity(actualStoragePath, entityCommand, getPassword)
        "transactions" -> transactions(actualStoragePath, transactionsCommand)
        else -> println("No command specified")
    }
}