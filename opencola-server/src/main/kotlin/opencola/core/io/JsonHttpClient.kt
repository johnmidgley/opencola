package opencola.core.io

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

class JsonHttpClient {
    val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

     inline fun <reified T> get(path: String) : T {
         // Yes - seems horrible to hide suspend calls, but otherwise the code is infected and much harder to debug
        return runBlocking { httpClient.get(path) }
    }

    fun put(path: String, value: Any) {
        return runBlocking {
            httpClient.put(path) {
                contentType(ContentType.Application.Json)
                body = value
            }
        }
    }
}