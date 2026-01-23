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

package opencola.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import opencola.server.ErrorResponse

fun Application.configureStatusPages() {
    val logger = KotlinLogging.logger("StatusPages")
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val response = ErrorResponse(cause.message ?: "Unknown")
            call.respond(HttpStatusCode.InternalServerError, Json.encodeToString(response))
            logger.error { cause }
        }
    }
}
