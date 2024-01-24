/*
 * Copyright 2024 OpenCola
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

package opencola.server.plugins.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path

fun Application.configureStorageRouting(storagePath: Path) {
    if(!storagePath.toFile().exists()) {
        storagePath.toFile().mkdirs()
    }

    routing {
        authenticate("auth-session") {
            get("/storage") {
                call.respond(storagePath.toFile().listFiles()?.map { it.name } ?: emptyList())
            }

            put("/storage/{filename}") {
                val filename = call.parameters["filename"]!!
                val file = storagePath.resolve(filename).toFile()
                call.receiveStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                call.response.status(HttpStatusCode.NoContent)
            }

            get("/storage/{filename}") {
                call.respondFile(storagePath.resolve(call.parameters["filename"]!!).toFile())
            }

            delete("/storage/{filename}") {
                val filename = call.parameters["filename"]!!
                val file = storagePath.resolve(filename).toFile()
                file.delete()
                call.response.status(HttpStatusCode.NoContent)
            }
        }
    }
}