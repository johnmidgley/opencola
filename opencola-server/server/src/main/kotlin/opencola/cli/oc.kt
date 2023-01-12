package opencola.cli

import io.opencola.core.config.Application
import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import io.opencola.core.model.SignedTransaction
import io.opencola.core.storage.EntityStore
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.math.exp

fun printUsage(){
    println("Usage: oc TASK COMMAND [ARGS] ")
}

fun transactionsFromEntityStore(entityStore: EntityStore, authorityIds: Iterable<Id> = emptyList()) : Sequence<SignedTransaction> {
    val batchSize = 50

    return sequence {
        var transactions = entityStore.getSignedTransactions(authorityIds, null, EntityStore.TransactionOrder.IdAscending, batchSize)

        while (true) {
            transactions.forEach{
                println("Writing ${it.transaction.id}")
                yield(it)
            }

            if(transactions.count() < batchSize){
                break
            }

            transactions = entityStore.getSignedTransactions(emptyList(), transactions.last().transaction.id, EntityStore.TransactionOrder.IdAscending, batchSize + 1).drop(1)
        }
    }
}

fun exportTransactions(entityStore: EntityStore, path: Path, authorityIds: Iterable<Id> = emptyList()) {
    path.outputStream().use { stream ->
        transactionsFromEntityStore(entityStore, authorityIds).forEach {
            println("Writing ${it.transaction.id}")
            SignedTransaction.encode(stream, it)
        }
    }
}

fun exportTransactions(entityStore: EntityStore, args: List<String>){
    if(args.count() != 1){
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

fun importTransactions(entityStore: EntityStore, args: List<String>){
        if(args.count() != 1){
        println("import transactions should have exactly 1 argument - filename")
        return
    }

    transactionsFromPath(Path(args.first())).forEach {
        println("Reading: ${it.transaction.id}")
        entityStore.addSignedTransactions(listOf(it))
    }
}

fun transactions(application: Application, args: Iterable<String>){
    val entityStore by application.injector.instance<EntityStore>()
    val commandArgs = args.drop(1)

    when(val command = args.first()){
        "export" -> exportTransactions(entityStore, commandArgs)
        "import" -> importTransactions(entityStore, commandArgs)
        else -> println("Unknown transaction command: $command")
    }

}

fun main(args: Array<String>) {
    if(args.size < 2) {
        printUsage()
        return
    }

    val task = args[0]
    // val taskArgs = args.asList().drop(1)

    when(args[0]){
        // "transactions" -> transactions(application, taskArgs)
        else -> println("Unknown task: $task")
    }
}