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

import com.github.ajalt.clikt.core.CliktCommand
import io.opencola.model.Id
import io.opencola.security.hash.Sha256Hash
import kotlin.io.path.Path

class OcCliktCommand : CliktCommand(name = "oc") {
    override fun run() = Unit
}

class PersonaCliktCommand : CliktCommand(name = "persona") {
    override fun run() = Unit
}

@OptIn(ExperimentalStdlibApi::class)
fun main(args: Array<String>) {
    val context = getContext(Path(System.getProperty("user.dir")))
    println("Storage path: ${context.storagePath}")

    for(i in 1..1000) {
        val id = Id.new()
        val idString = id.toString()
        val shaString = Sha256Hash.ofBytes(id.encoded()).toString()

        val hexString = id.encoded().toHexString()

        println("$idString $hexString $shaString")
    }




    // checkCopyEntityFileStorePaths(context.txFileStore)

//    println("Checking address book...")
//    checkAddressBook(context.addressBook)

//    println("Validating entity store db transactions...")
//    val dbTransactions = context.entityStore.getAllSignedTransactions()
//    checkTransactionIdUniqueness(dbTransactions)
//    checkTransactionAuthorities(context.addressBook, dbTransactions)

//    println("Validating entity store fs transactions...")
//    checkEntityFileStorePaths(context.txFileStore)
//    val fsTransactions = getEntityFileStoreTransactions(context.txFileStore)
//    checkTransactionIdUniqueness(fsTransactions)
//    checkTransactionAuthorities(context.addressBook, fsTransactions)
//
//    val dbTransactionIds = dbTransactions.map { it.transaction.id }.toSet()
//    val fsTransactionIds = fsTransactions.map { it.transaction.id }.toSet()

//    checkContentFileStoreIds(context.contentFileStore)

    println()


//    OcCliktCommand()
//        .subcommands(PersonaCliktCommand())
//        .main(args)
}