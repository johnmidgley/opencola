package io.opencola.cli

import io.opencola.security.MockKeyStore
import io.opencola.security.hash.Hash
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.ExposedEntityStoreContext
import java.nio.file.Path
import kotlin.io.path.createDirectory

fun entityStoreContext(storagePath: Path, passwordHash: Hash? = null): ExposedEntityStoreContext {
    return if(passwordHash != null)
        ExposedEntityStoreContext(storagePath, passwordHash)
    else
        ExposedEntityStoreContext(storagePath, keyStore =  MockKeyStore())
}

fun rebuildEntityStore(sourcePath: Path, passwordHash: Hash, destPath: Path) {
    if(!destPath.toFile().exists()) { destPath.createDirectory() }

    val sourceContext = ExposedEntityStoreContext(sourcePath, passwordHash)
    val destContext = ExposedEntityStoreContext(
        destPath,
        sourceContext.password,
        sourceContext.keyStore,
        sourceContext.signator,
        sourceContext.addressBook
    )

    println("Rebuilding entities from ${sourceContext.storagePath} in ${destContext.storagePath}")
    sourceContext.entityStore.getAllSignedTransactions().forEach { transaction ->
        println("+ ${transaction.transaction.id}")
        destContext.entityStore.addSignedTransactions(listOf(transaction))
    }
}

fun compareEntityStores(entityStore1: EntityStore, entityStore2: EntityStore) {
    val entityStore1Size = entityStore1.getEntities(emptySet(), emptySet()).size
    val entityStore2Size = entityStore2.getEntities(emptySet(), emptySet()).size

    if(entityStore1Size != entityStore2Size) {
        println("Entity store sizes differ: $entityStore1Size vs $entityStore2Size")
    }

    entityStore1.getEntities(emptySet(), emptySet()).forEach { entity1 ->
        entityStore2.getEntity(entity1.authorityId, entity1.entityId)?.let { entity2 ->
            if(entity1 != entity2) {
                println("Entity ${entity1.authorityId}:${entity1.entityId} differs")
                val diffs = entity1.diff(entity2)
                diffs.forEach { (fact1, fact2) ->
                    println("E1: $fact1")
                    println("E2: $fact2")
                }
            }
        } ?: println("Entity ${entity1.entityId} not found in entityStore2")
    }
}

fun cat(storagePath: Path, entityIdString: String) {
    val context = entityStoreContext(storagePath)
    val (authorityIds, entityIds) = parseEntityIdString(entityIdString)

    context.entityStore.getEntities(authorityIds, entityIds).forEach {
        it.getCurrentFacts().forEach { fact ->
            println(fact.toString().replace("\n", "\\n"))
        }
    }
}

fun ls(storagePath: Path) {
    val context = entityStoreContext(storagePath)
    context.entityStore.getEntities(emptySet(), emptySet()).forEach {
        println("${it.authorityId}:${it.entityId}")
    }
}

fun entity(storagePath: Path, entityCommand: EntityCommand, getPasswordHash: () -> Hash) {
    if(entityCommand.cat != null) {
        cat(storagePath, entityCommand.cat!!)
    } else if (entityCommand.ls == true) {
        ls(storagePath)
    } else if(entityCommand.rebuild != null) {
        rebuildEntityStore(storagePath, getPasswordHash(), storagePath.resolve("entity-rebuild-${System.currentTimeMillis()}"))
    } else if(entityCommand.cmp != null) {
        val entityStore1 = entityStoreContext(storagePath).entityStore
        val entityStore2 = entityStoreContext(Path.of(entityCommand.cmp!!)).entityStore
        compareEntityStores(entityStore1, entityStore2)
    }
}