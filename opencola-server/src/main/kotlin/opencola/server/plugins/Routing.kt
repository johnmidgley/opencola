package opencola.server.plugins

import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.application.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.request.*
import opencola.core.extensions.ifNotNullOrElse
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.model.DataEntity
import opencola.core.model.Id
import opencola.core.model.ResourceEntity
import opencola.core.storage.EntityStore
import opencola.core.storage.FileStore
import opencola.server.SearchHandler
import opencola.server.handleAction
import org.kodein.di.instance
import java.io.File
import kotlin.IllegalArgumentException

fun Application.configureRouting() {
    val injector = opencola.core.config.Application.instance.injector

    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        // Example of serving static html. Useful to serve base react response
        get("/index.html"){
            call.respondText(
                this.javaClass.classLoader.getResource("index.html")!!.readText(),
                ContentType.Text.Html
            )
        }

        get("/search"){
            SearchHandler(call).respond()
        }

        get("/entity"){
            // Handler that returns info on entity by URL
            TODO("Implement entity handler")
        }

        get("/data/{id}"){
             // Handler that returns data from datastore
            val stringId = call.parameters["id"]    ?: throw IllegalArgumentException("No id set")
            println("Data id: $stringId")
            val id = Id.fromHexString(stringId)
            val entityStore by injector.instance<EntityStore>()
            val authority by injector.instance<Authority>()
            val entity = entityStore.getEntity(authority, id)

            val dataEntity = when (entity) {
                is ResourceEntity -> entity.dataId.nullOrElse { entityStore.getEntity(authority, it) }
                is DataEntity -> entity
                else -> null
            } as DataEntity?

            if(dataEntity == null){
                call.respondText { "No data for id: $id" }
            } else {
                val fileStore by injector.instance<FileStore>()
                val bytes = fileStore.read(dataEntity.entityId)
                call.respondBytes{ bytes }
            }
        }

        post("/action"){
            val multipart = call.receiveMultipart()

            var action: String? = null
            var mhtml: ByteArray? = null

            multipart.forEachPart { part ->
                when(part){
                    is PartData.FormItem -> {
                        if(part.name != "action") throw IllegalArgumentException("Unknown FormItem in action request: ${part.name}")
                        action = part.value
                    }
                    is PartData.FileItem -> {
                        if(part.name != "mhtml") throw IllegalArgumentException("Unknown FileItem in action request: ${part.name}")
                        mhtml = part.streamProvider().use { it.readAllBytes() }
                    }
                    else -> throw IllegalArgumentException("Unknown part in request: ${part.name}")
                }
            }

            if(action == null){
                throw IllegalArgumentException("No action specified for request")
            }

            if(mhtml == null){
                throw IllegalArgumentException("No mhtml specified for request")
            }

            println("Action: $action Bytes: ${mhtml?.size}")
            handleAction(action as String, null, mhtml as ByteArray)
        }
    }
}
