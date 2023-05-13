package io.opencola.io

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking

val urlRegex = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]".toRegex()

class HttpClient() {
    private val httpClient = HttpClient(CIO)

    // TODO: This assumes html
    fun getBodyAsText(url: String): String {
        return runBlocking {
            val response = httpClient.get(url)
            response.bodyAsText()
        }
    }

    fun getContent(url: String): ByteArray {
        return runBlocking {
            httpClient.get(url).readBytes()
        }
    }
}
