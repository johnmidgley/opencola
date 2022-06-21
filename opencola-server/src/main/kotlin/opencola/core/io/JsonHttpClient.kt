package opencola.core.io

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.net.URI

class JsonHttpClient {
    val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

     inline fun <reified T> get(uri: URI) : T {
         // Yes - seems horrible to hide suspend calls, but otherwise the code is infected and much harder to debug
        return runBlocking { httpClient.get(uri.toString()) }
    }

    inline fun <reified T> post(uri: URI, value: Any) : T {
        return runBlocking {
            httpClient.post(uri.toString()) {
                contentType(ContentType.Application.Json)
                body = value
            }
        }
    }

    inline fun <reified T> put(uri: URI, value: Any) : T {
        return runBlocking {
            httpClient.put(uri.toString()) {
                contentType(ContentType.Application.Json)
                body = value
            }
        }
    }
}