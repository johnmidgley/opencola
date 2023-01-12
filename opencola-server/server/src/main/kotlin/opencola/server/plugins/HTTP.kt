package opencola.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureHTTP() {
    install(CORS) {
        // method(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete) // Clear entity and remove from solr
        // method(HttpMethod.Patch)
        allowHeader(HttpHeaders.ContentType)
        // header(HttpHeaders.Authorization)
        // header("MyCustomHeader")
        // allowCredentials = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }

}
