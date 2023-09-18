package opencola.server.plugins.routing

import io.ktor.http.*
import io.ktor.server.application.*
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
            call.respondRedirect("https://localhost:${serverConfig.ssl!!.port}/start")
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