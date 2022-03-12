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
import opencola.core.network.PeerRouter
import opencola.core.storage.EntityStore
import opencola.server.*
import opencola.service.EntityService
import opencola.service.search.SearchService
import org.kodein.di.instance
import kotlin.IllegalArgumentException
import kotlin.io.path.Path
import opencola.core.config.Application as app

// TODO: All routes should authenticate caller and authorize activity. Right now everything is open
fun Application.configureRouting(application: app) {
    val injector = application.injector
    val logger = app.instance.logger

    routing {
        get("/search"){
            val searchService by injector.instance<SearchService>()
            handleSearchCall(call, searchService)
        }

        get("/entity/{id}"){
            // TODO: Authority should be passed (and authenticated) in header
            val authority by injector.instance<Authority>()
            val entityStore by injector.instance<EntityStore>()
            handleEntityCall(call, authority.authorityId, entityStore)
        }

        get("/transactions/{authorityId}"){
            val entityStore by injector.instance<EntityStore>()
            handleTransactionsCall(call, entityStore)
        }

        get("/transactions/{authorityId}/{transactionId}"){
            val entityStore by injector.instance<EntityStore>()
            handleTransactionsCall(call, entityStore)
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
            val entityService by opencola.core.config.Application.instance.injector.instance<EntityService>()
            handleActionCall(action as String, value, entityService, mhtml as ByteArray)
            call.respond(HttpStatusCode.Accepted)
        }

        get("/actions/{uri}"){
            val authority by injector.instance<Authority>()
            val entityStore by injector.instance<EntityStore>()
            ActionsHandler(call, authority, entityStore).respond()
        }

        post("/notifications"){
            val entityService by injector.instance<EntityService>()
            val peerRouter by injector.instance<PeerRouter>()
            handlePostNotifications(call, entityService, peerRouter)
        }

        static(""){
            logger.info("Initializing static resources from ${Path(System.getProperty("user.dir"))} ")
            file("/", "resources/index.html")
            files("resources")
        }
    }
}
