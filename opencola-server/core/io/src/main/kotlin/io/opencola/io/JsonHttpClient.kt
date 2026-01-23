/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
        // Yes - seems horrible to hide suspend calls, but otherwise the code can't be used from Java and is harder to debug
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