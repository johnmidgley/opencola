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

fun exportTransactions(application: Application, args: List<String>){
    val entityStore by application.injector.instance<EntityStore>()

    if(args.count() != 1){
        println("export transactions should have exactly 1 argument - filename")
        return
    }

    val batchSize = 50
    println("Exporting transactions to: ${args.first()} batch size: $batchSize")

    Path(args.first()).outputStream().use { stream ->
        var transactions = entityStore.getSignedTransactions(emptyList(), null, EntityStore.TransactionOrder.Ascending, batchSize)

        while (true) {
            transactions.forEach{
                println("Writing ${it.transaction.id}")
                SignedTransaction.encode(stream, it)
            }

            if(transactions.count() < batchSize){
                break
            }

            transactions = entityStore.getSignedTransactions(emptyList(), transactions.last().transaction.id, EntityStore.TransactionOrder.Ascending, batchSize + 1).drop(1)
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

fun importTransactions(application: Application, args: List<String>){
        if(args.count() != 1){
        println("import transactions should have exactly 1 argument - filename")
        return
    }

    val entityStore by application.injector.instance<EntityStore>()
    transactionsFromPath(Path(args.first())).forEach {
        println("Reading: ${it.transaction.id}")
        entityStore.addSignedTransactions(listOf(it))
    }
}

fun transactions(application: Application, args: Iterable<String>){
    val commandArgs = args.drop(1)

    when(val command = args.first()){
        "export" -> exportTransactions(application, commandArgs)
        "import" -> importTransactions(application, commandArgs)
        else -> println("Unknown transaction command: $command")
    }

}

fun cleanStorage(application: Application, commandArgs: Iterable<String>) {
    TODO("Fix")
//    if (commandArgs.count() != 1 || commandArgs.first() != "-f"){
//        println("reset storage: illegal arguments ${commandArgs.joinToString(" ")}")
//        return
//    }
//
//    val storagePath = File(application.config.storage.path.toString())
//
//    println("Cleaning storage directory:")
//    val result = shellRun(storagePath){
//        command("./clean", listOf("-f"))
//    }
//    println(result)
}


fun storage(application: Application, args: Iterable<String>){
    val commandArgs = args.drop(1)

    when(val command = args.first()){
        "clean" -> cleanStorage(application, commandArgs)
        else -> println("Unknown storage command: $command")
    }
}



private val application: Application by lazy {
    TODO("Replace with proper config")
//    val applicationPath = Path(System.getProperty("user.dir"))
//    val config = loadConfig(applicationPath, "opencola-server.yaml")
//    val publicKey = Application.getOrCreateRootPublicKey(applicationPath.resolve(config.storage.path), config.security)
//    Application.instance(config, publicKey)
}

fun main(args: Array<String>) {
    if(args.size < 2) {
        printUsage()
        return
    }

    val task = args[0]
    val taskArgs = args.asList().drop(1)

    when(args[0]){
        "transactions" -> transactions(application, taskArgs)
        "storage" -> storage(application, taskArgs)
        else -> println("Unknown task: $task")
    }
}