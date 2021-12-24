package opencola.server

import opencola.core.content.Mhtml
import opencola.core.content.parseMhtml
import opencola.core.model.ResourceEntity

fun handleAction(action: String, value: String?, mhtml: ByteArray){
    val page = parseMhtml(mhtml.inputStream())

    when(action){
        "save" -> handleSaveAction(page)
        else -> throw NotImplementedError("No handler for $action")
    }
}

fun handleSaveAction(page: Mhtml?){
    if(page == null) throw RuntimeException("Unable to save page without body")

    println("here")
    // TODO: Make resource entity constructor for MHTML page?
    // TODO: Save mhtml
    // TODO: Add data id to resource entity - when indexing, index body from the dataEntity
    // TODO: Parse description
    // TODO: Load entity store in application.kt
    //entityStore.updateEntity(authority, ResourceEntity(authority.entityId, page.getUri(), name = page.getTitle()))
}