package opencola.core.network.zerotier

import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import kotlinx.serialization.json.Json as KotlinJson

// Json Serialization docs: https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md#json-configuration

class Client(private val authToken: String,
             private val basePath: String = "https://my.zerotier.com/api/v1") {
    private val httpClient = HttpClient {
        install(JsonFeature) {
            // TODO: isLenient and ignoreUnknownKeys should be false in integration tests
            serializer = KotlinxSerializer(KotlinJson { isLenient = true; ignoreUnknownKeys = true})

        }
    }

    private fun appendAuthorizationHeader(headers: HeadersBuilder) {
        headers.append(Authorization, "Bearer $authToken")
    }

    private suspend inline fun <reified T> httpGet(path: String) : T {
        return httpClient.get("$basePath/$path") {
            headers { appendAuthorizationHeader(this) }
        }
    }

    private suspend inline fun <reified T> httpPost(path: String, body: Any) : T {
        return httpClient.post("$basePath/$path") {
            headers { appendAuthorizationHeader(this) }
            contentType(ContentType.Application.Json)
            this.body = body
        }
    }

    private suspend inline fun <reified T> httpDelete(path: String) : T {
        return httpClient.delete("$basePath/$path") {
            headers { appendAuthorizationHeader(this) }
        }
    }

    suspend fun createNetwork(network: Network) : Network {
        return httpPost("network", network)
    }

    suspend fun getNetworks() : List<Network> {
        return httpGet("network")
    }

    suspend fun getNetwork(networkId: String) : Network {
        return httpGet("network/$networkId")
    }

    suspend fun deleteNetwork(networkId: String) {
        return httpDelete("network/$networkId")
    }

    suspend fun addNetworkMember(networkId: String, memberId: String, member: Member) : Member {
        return httpPost("network/$networkId/member/$memberId", member)
    }

    suspend fun getNetworkMembers(networkId: String) : List<Member> {
        return httpGet("network/$networkId/member")
    }

    suspend fun getNetworkMember(networkId: String, memberId: String) : Member {
        return httpGet("network/$networkId/member/$memberId")
    }

    suspend fun deleteNetworkMember(networkId: String, memberId: String) {
        return httpDelete("network/$networkId/member/$memberId")
    }
}