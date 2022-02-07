package opencola.server

import opencola.server.config.App
import opencola.core.content.MhtmlPage
import opencola.core.content.parseMhtml
import opencola.core.extensions.nullOrElse
import opencola.core.model.DataEntity
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
    ByteArrayOutputStream().use { outputStream ->
        writer.writeMessage(mhtmlPage.message, outputStream)
        val pageBytes = outputStream.toByteArray()
        val dataId = App.fileStore.write(pageBytes)
        val mimeType = App.textExtractor.getType(pageBytes)
        val resourceId = Id.ofUri(mhtmlPage.uri)
        val entity = (App.entityStore.getEntity(App.authority, resourceId) ?: ResourceEntity(
            App.authority.entityId,
            mhtmlPage.uri
        )) as ResourceEntity

        // Add / update fields
        entity.dataId = dataId
        entity.name = mhtmlPage.title
        entity.text = mhtmlPage.htmlText.nullOrElse { App.textExtractor.getBody(it.toByteArray()) }

        val dataEntity = (App.entityStore.getEntity(App.authority, dataId) ?: DataEntity(App.authority.entityId, dataId, mimeType))

        // TODO: Remove authority from update calls - authority id is in entity
        // TODO: Make update entity take vargs of entities so only single transaction needed
        App.entityStore.commitChanges(entity, dataEntity)
        App.searchService.index(entity)
    }
}