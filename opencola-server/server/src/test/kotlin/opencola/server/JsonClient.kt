package opencola.server

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*

// Class that makes it more convenient to make JSON requests
class JsonClient(builder: ApplicationTestBuilder) {
    val httpClient = builder.createClient {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend inline fun get(
        path: String,
    ): HttpResponse {
        return httpClient.get(path)
    }

    suspend inline fun <reified T> post(
        path: String,
        body: T,
    ): HttpResponse {
        return httpClient.post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend inline fun delete(
        path: String,
    ): HttpResponse {
        return httpClient.delete(path)
    }
}