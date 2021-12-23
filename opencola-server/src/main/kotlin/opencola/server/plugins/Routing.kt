package opencola.server.plugins

import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.request.*
import opencola.core.content.parseMhtml
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

        post("/action"){
            val multipart = call.receiveMultipart()

            var action: String? = null
            var mhtml: ByteArray? = null

            multipart.forEachPart {
                when(it){
                    is PartData.FormItem -> {
                        if(it.name != "action") throw IllegalArgumentException("Unknown FormItem in action request: ${it.name}")
                        action = it.value
                    }
                    is PartData.FileItem -> {
                        if(it.name != "mhtml") throw IllegalArgumentException("Unknown FileItem in action request: ${it.name}")
                        mhtml = it.streamProvider().readAllBytes()
                    }
                    else -> throw IllegalArgumentException("Unknown part in request: ${it.name}")
                }
            }

            if(action == null){
                throw IllegalArgumentException("No action specified for request")
            }

            if(mhtml == null){
                throw IllegalArgumentException("No mhtml specified for request")
            }

            println("Action: $action Bytes: ${mhtml?.size}")
            val result = parseMhtml((mhtml as ByteArray).inputStream())
            println(result?.messageId)

        }
    }
}
