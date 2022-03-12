package opencola.server

import opencola.core.config.Application
import opencola.core.content.parseMhtml
import opencola.core.model.*
import opencola.service.EntityService
import org.kodein.di.instance

fun handleActionCall(action: String, value: String?, entityService: EntityService, mhtml: ByteArray) {
    val mhtmlPage = mhtml.inputStream().use { parseMhtml(it) ?: throw RuntimeException("Unable to parse mhtml") }

    val actions = when(action){
        "save" -> Actions()
        "like" -> Actions(like = value?.toBooleanStrict() ?: throw RuntimeException("No value specified for like"))
        "trust" -> Actions(trust = value?.toFloat() ?: throw RuntimeException("No value specified for trust"))
        else -> throw NotImplementedError("No handler for $action")
    }

    entityService.updateResource(mhtmlPage, actions)
}
