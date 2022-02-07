package opencola.server.plugins

import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.request.*
import opencola.server.SearchHandler
import opencola.server.handleAction
import java.lang.IllegalArgumentException

fun Application.configureRouting() {
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
