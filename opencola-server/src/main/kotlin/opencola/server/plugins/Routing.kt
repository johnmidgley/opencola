package opencola.server.plugins

import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*

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
            val body = call.receiveText()
            println(body)
            println(body.length)
        }
    }
}
