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

package opencola.server.plugins.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.opencola.application.LoginConfig
import io.opencola.application.SSLConfig
import io.opencola.security.hash.Sha256Hash
import kotlinx.coroutines.CompletableDeferred
import opencola.server.AuthToken
import opencola.server.LoginCredentials
import opencola.server.handlers.validateAuthorityKeyStorePassword
import opencola.server.plugins.UserSession
import opencola.server.view.loginPage
import java.nio.file.Path
import javax.crypto.SecretKey

private fun ApplicationCall.getAuthToken(authSecretKey: SecretKey): AuthToken? {
    return sessions.get<UserSession>()?.decodeAuthToken(authSecretKey)
}

const val DEFAULT_USERNAME = "oc"

fun Application.configureLoginRouting(
    storagePath: Path,
    sslConfig: SSLConfig?,
    loginConfig: LoginConfig,
    authSecretKey: SecretKey,
    loginCredentials: CompletableDeferred<LoginCredentials>
) {
    routing {
        get("/login") {
            // A call from the toolbar may attempt to log in. If the server hasn't started, it needs to be started
            if (!loginCredentials.isCompleted)
                call.respondRedirect("/start")
            else if (call.request.origin.scheme != "https" && sslConfig != null) {
                call.respondRedirect("https://${call.request.host()}:${sslConfig.port}/login")
            } else {
                // val username = call.getAuthToken(authEncryptionParams)?.username ?: app.config.security.login.username
                loginPage(call)
            }
        }

        post("/login") {
            if (call.request.origin.scheme != "https") {
                throw RuntimeException("Attempt to post login credentials over non-https connection.")
            }

            val formParameters = call.receiveParameters()
            val username = DEFAULT_USERNAME
            val password = formParameters["password"]

            if (password.isNullOrBlank()) {
                loginPage(call, "Please enter a password")
            } else if (validateAuthorityKeyStorePassword(storagePath, Sha256Hash.ofString(password))) {
                val authToken = AuthToken(username).encode(authSecretKey)
                call.sessions.set(UserSession(authToken))
                call.respondRedirect("/")
            } else {
                loginPage(call, "Bad password")
            }
        }

        get("logout") {
            val username = call.getAuthToken(authSecretKey)?.username ?: loginConfig.username
            val authToken = AuthToken(username, -1).encode(authSecretKey)
            call.sessions.set(UserSession(authToken))
            call.respondRedirect("/login")
        }

        get("/isLoggedIn") {
            val authToken = call.getAuthToken(authSecretKey)
            if (authToken?.isValid() == true)
                call.respond(HttpStatusCode.OK)
            else
                call.respond(HttpStatusCode.Unauthorized)
        }
    }


}