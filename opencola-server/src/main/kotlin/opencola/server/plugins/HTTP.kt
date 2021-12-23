package opencola.server.plugins

import io.ktor.http.*
import io.ktor.features.*
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*

fun Application.configureHTTP() {
    install(CORS) {
        // method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        // method(HttpMethod.Put)
        method(HttpMethod.Delete) // Clear entity and remove from solr
        // method(HttpMethod.Patch)
        header(HttpHeaders.ContentType)
        // header(HttpHeaders.Authorization)
        // header("MyCustomHeader")
        // allowCredentials = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }

}
