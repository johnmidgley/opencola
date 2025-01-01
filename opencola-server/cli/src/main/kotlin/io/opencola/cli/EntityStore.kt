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
import io.opencola.model.SignedTransaction
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.filestore.FileSystemIdAddressedFileStore
import io.opencola.storage.filestore.IdAddressedFileStore
import java.nio.file.Files

fun checkTransactionAuthorities(addressBook: AddressBook, signedTransactions: Sequence<SignedTransaction>) {
    val knownAuthorityIds = addressBook.getEntries().map { it.entityId }.toSet()

    for(signedTransaction in signedTransactions) {
        val transaction = signedTransaction.transaction

        if(!knownAuthorityIds.contains(transaction.authorityId)){
            println("ERROR: Transaction ${transaction.id} has unknown authority ${transaction.authorityId}")
        }
    }
}

fun checkTransactionIdUniqueness(signedTransactions: Sequence<SignedTransaction>) {
    val transactionIds = signedTransactions.map { it.transaction.id }
    val countIds = transactionIds.count()
    val uniqueIdCount = transactionIds.toSet().count()

    if(countIds != uniqueIdCount)
        println("ERROR: Transaction id count ($countIds) != unique count ($uniqueIdCount)")
}

fun checkEntityFileStorePaths(fileStore: IdAddressedFileStore) {
    fileStore.enumerateIds().forEach {
        SignedTransaction.decodeProto(fileStore.read(it)!!).also { tx ->
            if(tx.transaction.id != it) {
                    println("ERROR: Enumerated id:${it} != transaction id:${tx.transaction.id}")
            }
        }
    }
}


fun getEntityFileStoreTransactions(fileStore: IdAddressedFileStore): Sequence<SignedTransaction> {
    return fileStore.enumerateIds().map { SignedTransaction.decodeProto(fileStore.read(it)!!) }
}

fun copyEntityFileStore(fileStore: IdAddressedFileStore) : IdAddressedFileStore {
    val tempDirectory = Files.createTempDirectory("copyEntityFileStore_${System.currentTimeMillis()}")
    val tempFileStore = FileSystemIdAddressedFileStore(tempDirectory)

    fileStore.enumerateIds().forEach {
        val bytes = fileStore.read(it)!!
        val signedTransaction = SignedTransaction.decodeProto(bytes)

        if(signedTransaction.transaction.id == Id.decode("3KVhvn5TnwnvXdBefgXG6QhhshsQV2W8RypwTiuSr67g"))
            println()

        tempFileStore.write(signedTransaction.transaction.id, bytes)
    }

    return tempFileStore
}

fun checkCopyEntityFileStorePaths(fileStore: IdAddressedFileStore) {
    println("--> checkCopyEntityFileStorePaths")
    val tempFileStore = copyEntityFileStore(fileStore)
    checkEntityFileStorePaths(tempFileStore)
    println("<-- checkCopyEntityFileStorePaths")
}

fun check3kVhvn5TnwnvXdBefgXG6QhhshsQV2W8RypwTiuSr67g(fileStore: IdAddressedFileStore) {
    val tempDirectory = Files.createTempDirectory("copyEntityFileStore_${System.currentTimeMillis()}")
    val tempFileStore = FileSystemIdAddressedFileStore(tempDirectory)
    val tx = getEntityFileStoreTransactions(fileStore).single { tx -> tx.transaction.id == Id.decode("3KVhvn5TnwnvXdBefgXG6QhhshsQV2W8RypwTiuSr67g")}
    tempFileStore.write(tx.transaction.id, tx.encodeProto())

    println("${tx.transaction.id}")
    println("${tempFileStore.enumerateIds().first()}")
}