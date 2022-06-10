package opencola.core.network.zerotier

import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders.Authorization
import kotlinx.serialization.json.Json as KotlinJson

// Json Serialization docs: https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md#json-configuration

class Client {
    private val basePath = "https://my.zerotier.com/api/v1"
    private val authToken = ""
    private val httpClient = HttpClient {
        install(JsonFeature) {
            // TODO: isLenient and ignoreUnknownKeys should be false in integration tests
            serializer = KotlinxSerializer(KotlinJson { isLenient = true; ignoreUnknownKeys = true})

        }
    }

    private suspend inline fun <reified T> httpGet(path: String) : T {
        return httpClient.get("$basePath/$path") {
            headers {
                append(Authorization, "Bearer $authToken")
            }
        }
    }

    suspend fun getNetworks() : List<Network> {
        return httpGet("network")
    }

}