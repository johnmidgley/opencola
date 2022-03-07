package opencola.server

import opencola.core.config.Application
import opencola.core.content.MhtmlPage
import opencola.core.content.TextExtractor
import opencola.core.content.parseMhtml
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.model.DataEntity
import opencola.core.model.Id
import opencola.core.model.ResourceEntity
import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore
import opencola.core.storage.FileStore
import org.apache.james.mime4j.message.DefaultMessageWriter
import org.kodein.di.instance
import java.io.ByteArrayOutputStream

fun handleAction(action: String, value: String?, mhtml: ByteArray) {
    val page = mhtml.inputStream().use { parseMhtml(it) }

    when(action){
        "save" -> handleSaveAction(page)
        else -> throw NotImplementedError("No handler for $action")
    }
}

fun handleSaveAction(mhtmlPage: MhtmlPage?){
    if(mhtmlPage == null) throw RuntimeException("Unable to save page without body")

    // TODO - Inject the properly as class parameters
    val authority by Application.instance.injector.instance<Authority>()
    val fileStore by Application.instance.injector.instance<FileStore>()
    val textExtractor by Application.instance.injector.instance<TextExtractor>()
    val entityStore by Application.instance.injector.instance<EntityStore>()
    val searchService by Application.instance.injector.instance<SearchIndex>()

    // TODO: Add data id to resource entity - when indexing, index body from the dataEntity
    // TODO: Parse description
    // TODO - EntityStore should detect if a duplicate entity is added. Just merge it?
    val writer = DefaultMessageWriter()
    ByteArrayOutputStream().use { outputStream ->
        writer.writeMessage(mhtmlPage.message, outputStream)
        val pageBytes = outputStream.toByteArray()
        val dataId = fileStore.write(pageBytes)
        val mimeType = textExtractor.getType(pageBytes)
        val resourceId = Id.ofUri(mhtmlPage.uri)
        val entity = (entityStore.getEntity(authority.authorityId, resourceId) ?: ResourceEntity(
            authority.entityId,
            mhtmlPage.uri
        )) as ResourceEntity

        // Add / update fields
        entity.dataId = dataId
        entity.name = mhtmlPage.title
        entity.text = mhtmlPage.htmlText.nullOrElse { textExtractor.getBody(it.toByteArray()) }

        val dataEntity = (entityStore.getEntity(authority.authorityId, dataId) ?: DataEntity(authority.entityId, dataId, mimeType))

        // TODO: Remove authority from update calls - authority id is in entity
        // TODO: Make update entity take vargs of entities so only single transaction needed
        entityStore.commitChanges(entity, dataEntity)
        searchService.index(entity)
    }
}