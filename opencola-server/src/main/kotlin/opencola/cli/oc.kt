package opencola.cli

import opencola.core.config.Application
import opencola.core.config.loadConfig
import opencola.core.model.Id
import opencola.core.model.SignedTransaction
import opencola.core.storage.EntityStore
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import com.lordcodes.turtle.shellRun
import java.io.File

fun printUsage(){
    println("Usage: oc TASK COMMAND [ARGS] ")
}

fun exportTransactions(entityStore: EntityStore, args: List<String>){
    if(args.count() != 1){
        println("export transactions should have exactly 1 argument - filename")
        return
    }

    val batchSize = 50
    println("Exporting transactions to: ${args.first()} batch size: $batchSize")

    Path(args.first()).outputStream().use { stream ->
        var transactions = entityStore.getSignedTransactions(emptyList(), null, EntityStore.TransactionOrder.IdAscending, batchSize)

        while (true) {
            transactions.forEach{
                println("Writing ${it.transaction.id}")
                SignedTransaction.encode(stream, it)
            }

            if(transactions.count() < batchSize){
                break
            }

            transactions = entityStore.getSignedTransactions(emptyList(), transactions.last().transaction.id, EntityStore.TransactionOrder.IdAscending, batchSize + 1).drop(1)
        }
    }
}

private fun transactionsFromPath(path: Path): Sequence<SignedTransaction> {
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