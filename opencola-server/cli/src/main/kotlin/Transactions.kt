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

package io.opencola.cli

import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.model.TransactionFact
import io.opencola.model.value.EmptyValue
import io.opencola.storage.entitystore.EntityStore
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

// TODO: Use EntityStore.getAllTransactions() instead of this (pull this logic inside)
fun transactionsFromEntityStore(
    entityStore: EntityStore,
    authorityIds: Set<Id> = emptySet()
): Sequence<SignedTransaction> {
    val batchSize = 50

    return sequence {
        var transactions =
            entityStore.getSignedTransactions(authorityIds, null, EntityStore.TransactionOrder.IdAscending, batchSize)

        while (true) {
            transactions.forEach {
                println("Writing ${it.transaction.id}")
                yield(it)
            }

            if (transactions.count() < batchSize) {
                break
            }

            transactions = entityStore.getSignedTransactions(
                emptySet(),
                transactions.last().transaction.id,
                EntityStore.TransactionOrder.IdAscending,
                batchSize + 1
            ).drop(1)
        }
    }
}

fun exportTransactions(entityStore: EntityStore, path: Path, authorityIds: Set<Id> = emptySet()) {
    path.outputStream().use { stream ->
        transactionsFromEntityStore(entityStore, authorityIds).forEach {
            println("Writing ${it.transaction.id}")
            SignedTransaction.encode(stream, it)
        }
    }
}

fun exportTransactions(entityStore: EntityStore, args: List<String>) {
    if (args.count() != 1) {
        println("export transactions should have exactly 1 argument - filename")
        return
    }

    println("Exporting transactions to: ${args.first()}")
    exportTransactions(entityStore, Path(args.first()))
}

fun transactionsFromPath(path: Path): Sequence<SignedTransaction> {
    return sequence {
        path.inputStream().use {
            // TODO: Make sure available works properly. From the docs, it seems like it can return 0 when no buffered data left.
            // Can't find the idiomatic way to check for end of file. May need to read until exception??
            while (it.available() > 0)
                yield(SignedTransaction.decode(it))

            if (it.read() != -1) {
                throw RuntimeException("While reading transactions, encountered available() == 0 but read() != -1")
            }
        }
    }
}

fun importTransactions(entityStore: EntityStore, args: List<String>) {
    if (args.count() != 1) {
        println("import transactions should have exactly 1 argument - filename")
        return
    }

    transactionsFromPath(Path(args.first())).forEach {
        println("Reading: ${it.transaction.id}")
        entityStore.addSignedTransactions(listOf(it))
    }
}


fun factValueToString(fact: TransactionFact): String {
    return if (fact.value is EmptyValue)
        ""
    else
        fact.attribute.valueWrapper.unwrap(fact.value)
            .toString()
            .replace("\n", "\\n")
            .take(20)
}


fun grep(storagePath: Path, entityIdString: String) {
    val context = entityStoreContext(storagePath)
    val (authorityIds, entityIds) = parseEntityIdString(entityIdString)

    context.entityStore.getAllSignedTransactions(authorityIds).forEach { signedTransaction ->
        signedTransaction.transaction.transactionEntities.forEach { transactionEntity ->
            if (transactionEntity.entityId in entityIds) {
                transactionEntity.facts.forEach { fact ->
                    println(
                        "${signedTransaction.transaction.authorityId} | ${transactionEntity.entityId} |  ${fact.attribute.name} | " +
                                factValueToString(fact) + " | ${fact.operation} | ${signedTransaction.transaction.epochSecond}"
                    )
                }
            }
        }
    }
}

fun transactions(storagePath: Path, transactionsCommand: TransactionsCommand) {
    if (transactionsCommand.grep != null) {
        grep(storagePath, transactionsCommand.grep!!)
        return
    }
}