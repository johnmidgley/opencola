package opencola.server

import opencola.core.content.MhtmlPage
import opencola.core.content.parseMhtml
import opencola.core.model.Id
import opencola.core.model.ResourceEntity

fun handleAction(action: String, value: String?, mhtml: ByteArray){
    val page = parseMhtml(mhtml.inputStream())

    when(action){
        "save" -> handleSaveAction(page)
        else -> throw NotImplementedError("No handler for $action")
    }
}

fun handleSaveAction(mhtmlPage: MhtmlPage?){
    if(mhtmlPage == null) throw RuntimeException("Unable to save page without body")

    // TODO: Make resource entity constructor for MHTML page?
    // TODO: Save mhtml to file store.
    // val writer = DefaultMessageWriter()
    // writer.writeMessage(page.message, File("/Users/johnmidgley/tmp/ex.mht").outputStream())
    // TODO: Add data id to resource entity - when indexing, index body from the dataEntity
    // TODO: Parse description
    // TODO - EntityStore should detect if a duplicate entity is added. Just merge it?
    val resourceId = Id(mhtmlPage.uri)
    val entity = (entityStore.getEntity(authority, resourceId) ?: ResourceEntity(authority.entityId, mhtmlPage.uri)) as ResourceEntity

    entity.name = mhtmlPage.title
    entityStore.updateEntity(authority, entity)
}