package io.opencola.io

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.net.URI

class JsonHttpClient {
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    inline fun <reified T> get(uri: URI): T {
        // Yes - seems horrible to hide suspend calls, but otherwise the code is infected and much harder to debug
        return runBlocking { httpClient.get(uri.toString()).body() }
    }

    inline fun <reified T> post(uri: URI, value: Any): T {
        return runBlocking {
            httpClient.post(uri.toString()) {
                contentType(ContentType.Application.Json)
                setBody(value)
            }.body()
        }
    }

    inline fun <reified T> put(uri: URI, value: Any): T {
        return runBlocking {
            httpClient.put(uri.toString()) {
                contentType(ContentType.Application.Json)
                setBody(value)
            }.body()
        }
    }
}