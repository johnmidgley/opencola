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
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opencola.application.ServerConfig
import io.opencola.system.OS
import io.opencola.system.getOS
import io.opencola.system.installTrustedCACert
import io.opencola.system.setCertInstalled
import java.nio.file.Path
import kotlin.io.path.readBytes

fun Application.configureCertificateRouting(storagePath: Path, serverConfig: ServerConfig){
    routing {
        get("/installCert") {
            // FYI - linux only supports pem. Mac support both der and pem. Windows only supports der
            val certType = if (getOS() == OS.Windows) "der" else "pem"
            val certName = "opencola-ssl.$certType"
            val certPath = storagePath.resolve("cert/$certName")
            val os = getOS()

            if (os == OS.Mac || os == OS.Windows) {
                installTrustedCACert(storagePath)
                call.respondRedirect("/start")
            } else {
                // Send the raw cert for manual installation
                call.response.header("Content-Disposition", "attachment; filename=\"$certName\"")
                call.respondBytes(certPath.readBytes(), ContentType("application", "x-x509-ca-cert"))
            }
        }

        post("/certInstalled") {
            setCertInstalled(storagePath)
            call.respondRedirect("https://${call.request.host()}:${serverConfig.ssl!!.port}/start")
        }

        get("/cert") {
            // FYI - linux only supports pem. Mac support both der and pem. Windows only supports der
            val certType = if (getOS() == OS.Windows) "der" else "pem"
            val certName = "opencola-ssl.$certType"
            val certPath = storagePath.resolve("cert/$certName")
            call.response.header("Content-Disposition", "attachment; filename=\"$certName\"")
            call.respondBytes(certPath.readBytes(), ContentType("application", "x-x509-ca-cert"))
        }
    }
}