package io.opencola.cli

import io.opencola.application.indexTransaction
import io.opencola.model.Entity
import io.opencola.model.Id
import io.opencola.search.LuceneSearchIndex
import io.opencola.search.SearchIndex
import io.opencola.storage.*
import kotlinx.cli.ExperimentalCli
import java.nio.file.Path
import kotlin.io.path.createDirectory

fun search(entities: List<Entity>, query: String): List<Entity> {
    return entities.filter {
        it.name?.contains(query, true) == true ||
                it.description?.contains(query, true) == true ||
                it.text?.contains(query, true) == true
    }
}

fun diffEntityStoreAndSearchIndex(entityStore: EntityStore, searchIndex: SearchIndex, authority: AddressBookEntry): Set<Id> {
    val entities = search(entityStore.getEntities(setOf(authority.entityId), emptySet()), "")
    val searchResults = searchIndex.search(setOf(authority.entityId), "", 1000)
    return entities.map { it.entityId }.toSet().minus(searchResults.map { it.entityId }.toSet())
}

fun compareSearchIndexToEntityStore(storagePath: Path) {
    val context = entityStoreContext(storagePath)
    val searchIndex = LuceneSearchIndex(storagePath.resolve("lucene"))

    context.addressBook.getEntries().forEach { authority ->
        println("Comparing entities for persona: ${authority.name}")
        diffEntityStoreAndSearchIndex(context.entityStore, searchIndex, authority).forEach {
            println("- $it")
        }
        println("")
    }
}

fun rebuildSearchIndex(sourcePath: Path, destPath: Path) {
    if(!destPath.toFile().exists()) { destPath.createDirectory() }
    // TODO: Password not needed for this
    val sourceContext = entityStoreContext(sourcePath)
    println("Rebuilding index from ${sourceContext.storagePath} in $destPath")

    LuceneSearchIndex(destPath.resolve("lucene")).use { destSearchIndex ->
        sourceContext.entityStore.getAllTransactions().forEach { transaction ->
            println("+ ${transaction.transaction.id}")
            indexTransaction(sourceContext.entityStore, destSearchIndex, transaction)
        }
        destSearchIndex.forceMerge()
    }
}

fun patchIndexFromEntityStore(storagePath: Path) {
    val context = entityStoreContext(storagePath)
    val searchIndex = LuceneSearchIndex(storagePath.resolve("lucene"))

    context.addressBook.getEntries().forEach { authority ->
        println("Patching index for persona: ${authority.name}")
        diffEntityStoreAndSearchIndex(context.entityStore, searchIndex, authority).forEach {
            println("+ $it")
            searchIndex.add(context.entityStore.getEntity(authority.entityId, it)!!)
        }
        println("")
    }
}

fun optimize(storagePath: Path) {
    LuceneSearchIndex(storagePath.resolve("lucene")).use { searchIndex ->
        println("Optimizing search index")
        searchIndex.forceMerge()
    }
}

@ExperimentalCli
fun search(storagePath: Path, searchCommand: SearchCommand) {
    // TODO: Validate search command options
    if(searchCommand.rebuild == true){
        rebuildSearchIndex(storagePath, storagePath.resolve("search-rebuild-${System.currentTimeMillis()}"))
    } else if (searchCommand.compare == true) {
        compareSearchIndexToEntityStore(storagePath)
    } else if (searchCommand.patch == true) {
        patchIndexFromEntityStore(storagePath)
    } else if(searchCommand.optimize == true) {
        optimize(storagePath)
    } else {
        println("No search command specified")
    }
}