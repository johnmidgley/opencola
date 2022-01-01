package opencola.server

import opencola.core.content.MhtmlPage
import opencola.core.content.parseMhtml
import opencola.core.extensions.nullOrElse
import opencola.core.model.Id
import opencola.core.model.ResourceEntity
import org.apache.james.mime4j.message.DefaultMessageWriter
import java.io.ByteArrayOutputStream

fun handleAction(action: String, value: String?, mhtml: ByteArray){
    val page = mhtml.inputStream().use { parseMhtml(it) }

    when(action){
        "save" -> handleSaveAction(page)
        else -> throw NotImplementedError("No handler for $action")
    }
}

fun handleSaveAction(mhtmlPage: MhtmlPage?){
    if(mhtmlPage == null) throw RuntimeException("Unable to save page without body")

    // TODO: Add data id to resource entity - when indexing, index body from the dataEntity
    // TODO: Parse description
    // TODO - EntityStore should detect if a duplicate entity is added. Just merge it?
    val writer = DefaultMessageWriter()
    ByteArrayOutputStream().use { bufferedOutputStream ->
        writer.writeMessage(mhtmlPage.message, bufferedOutputStream)
        val pageBytes = bufferedOutputStream.toByteArray()
        val dataId = fileStore.write(pageBytes)
        val resourceId = Id.ofUri(mhtmlPage.uri)
        val entity = (entityStore.getEntity(authority, resourceId) ?: ResourceEntity(
            authority.entityId,
            mhtmlPage.uri
        )) as ResourceEntity

        // Add / update fields
        entity.dataId = dataId
        entity.name = mhtmlPage.title
        entity.text = mhtmlPage.htmlText.nullOrElse { textExtractor.getBody(it.toByteArray()) }

        entityStore.updateEntity(authority, entity)
        searchService.index(entity)
    }
}