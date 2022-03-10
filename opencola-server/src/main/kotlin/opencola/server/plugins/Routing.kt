package opencola.server.plugins

import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.request.*
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.storage.EntityStore
import opencola.server.*
import org.kodein.di.instance
import kotlin.IllegalArgumentException
import kotlin.io.path.Path
import opencola.core.config.Application as app

// TODO: All routes should authenticate caller and authorize activity. Right now everything is open
fun Application.configureRouting() {
    val injector = opencola.core.config.Application.instance.injector
    val logger = app.instance.logger

    routing {
        get("/search"){
            SearchHandler(call).respond()
        }

        get("/entity/{id}"){
            // Handler that returns info on entity by URL
            // TODO: Authority should be passed (and authenticated) in header
            val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")
            val authority by injector.instance<Authority>()
            val entityStore by injector.instance<EntityStore>()

            val entity = entityStore.getEntity(authority.authorityId, Id.fromHexString(stringId))

            if(entity != null)
                call.respond(entity.getFacts())

        }

        get("/transactions/{authorityId}"){
            TransactionsHandler(call).respond()
        }

        get("/transactions/{authorityId}/{transactionId}"){
            TransactionsHandler(call).respond()
        }

        post("/transactions"){
            handlePostTransactions(app.instance, call)
        }

        get("/data/{id}/{partName}"){
            // TODO: Add a parameters extension that gets the parameter value or throws an exception
            val dataHandler by injector.instance<DataHandler>()
            val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")
            val partName = call.parameters["partName"] ?: throw IllegalArgumentException("No partName set")

            val bytes = dataHandler.getDataPart(Id.fromHexString(stringId), partName)
            if(bytes != null){
                val contentType = ContentType.fromFilePath(partName).firstOrNull()
                call.respondBytes(bytes, contentType = contentType)
            }
        }

        get("/data/{id}"){
             // Handler that returns data from datastore
            val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")
            println("Data id: $stringId")
            val id = Id.fromHexString(stringId)
            val dataHandler by injector.instance<DataHandler>()

            val data = dataHandler.getData(id)

            if(data == null){
                call.respondText(status = HttpStatusCode.NoContent) { "No data for id: $id" }
            } else {
                call.respondBytes(data, ContentType.Application.OctetStream)
            }
        }

        post("/action"){
            val multipart = call.receiveMultipart()

            var action: String? = null
            var value: String? = null
            var mhtml: ByteArray? = null

            multipart.forEachPart { part ->
                when(part){
                    is PartData.FormItem -> {
                        when(part.name){
                            "action" -> action = part.value
                            "value" -> value = part.value
                            else -> throw IllegalArgumentException("Unknown FormItem in action request: ${part.name}")
                        }
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

            if(value == null){
                throw IllegalArgumentException("No value specified for request")
            }

            if(mhtml == null){
                throw IllegalArgumentException("No mhtml specified for request")
            }

            println("Action: $action Bytes: ${mhtml?.size}")
            handleAction(action as String, value, mhtml as ByteArray)
            call.respond(HttpStatusCode.Accepted)
        }

        get("/status/{uri}"){
            StatusHandler(call).respond()
        }

        static(""){
            logger.info("Initializing static resources from ${Path(System.getProperty("user.dir"))} ")
            file("/", "resources/index.html")
            files("resources")
        }
    }
}
