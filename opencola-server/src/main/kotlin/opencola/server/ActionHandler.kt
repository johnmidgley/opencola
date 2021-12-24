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
    val entity = ResourceEntity(authority.entityId, page.getUri(), name = page.getTitle())

}