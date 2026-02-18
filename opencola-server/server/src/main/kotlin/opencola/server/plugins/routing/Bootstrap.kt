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

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opencola.application.LoginConfig
import io.opencola.application.ServerConfig
import io.opencola.security.hash.Sha256Hash
import io.opencola.security.keystore.defaultPasswordHash
import io.opencola.system.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import opencola.server.AuthToken
import opencola.server.LoginCredentials
import opencola.server.handlers.changeAuthorityKeyStorePassword
import opencola.server.handlers.isNewUser
import opencola.server.handlers.validateAuthorityKeyStorePassword
import opencola.server.view.changePasswordForm
import opencola.server.view.newUserForm
import opencola.server.view.startingPage
import opencola.server.view.startupForm
import java.nio.file.Path
import javax.crypto.SecretKey
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

// TODO: All routes should authenticate caller and authorize activity. Right now everything is open
fun Application.configureBootstrapRouting(
    storagePath: Path,
    serverConfig: ServerConfig,
    loginConfig: LoginConfig,
    authSecretKey: SecretKey,
    loginCredentials: CompletableDeferred<LoginCredentials>,
) {
    val migratingData =
        storagePath.resolve("address-book.db").exists() && !storagePath.resolve("address-book").isDirectory()

    configureLoginRouting(storagePath, serverConfig.ssl, loginConfig, authSecretKey, loginCredentials)
    configureCertificateRouting(storagePath, serverConfig)

    routing {
        get("/start") {
            if(loginCredentials.isCompleted) {
                call.respondRedirect("/")
            } else {
                val isNewUser = isNewUser(storagePath)

                if (serverConfig.ssl != null && !isCertInstalled(storagePath)) {
                    call.respondRedirect("/init/installCert.html")
                } else if (isNewUser) {
                    call.respondRedirect("newUser")
                } else if (call.request.origin.scheme != "https") {
                    call.respondRedirect("https://${call.request.host()}:${serverConfig.ssl!!.port}/start")
                } else {
                    // val username = call.getAuthToken(authEncryptionParams)?.username ?: loginConfig.username
                    startupForm(call)
                }
            }
        }

        post("/start") {
            require(!loginCredentials.isCompleted)

            val formParameters = call.receiveParameters()
            // TODO: User should be able to choose username for higher (external) security.
            //  This needs to be stored in the keystore, and a change username flow needs to be implemented.
            val username = DEFAULT_USERNAME
            val password = formParameters["password"]

            if (password.isNullOrBlank()) {
                startupForm(call, "Please enter a password")
            } else {
                val passwordHash = Sha256Hash.ofString(password)
                if (validateAuthorityKeyStorePassword(storagePath, passwordHash)) {
                    startingPage(call, AuthToken(username).encode(authSecretKey), migratingData)
                    loginCredentials.complete(LoginCredentials(username, passwordHash))
                } else
                    startupForm(call, "Incorrect password")
            }
        }

        get("/newUser") {
            require(!loginCredentials.isCompleted)

            if (call.request.origin.scheme != "https") {
                call.respondRedirect("https://${call.request.host()}:${serverConfig.ssl!!.port}/newUser")
            } else {
                newUserForm(call)
            }
        }

        post("/newUser") {
            require(!loginCredentials.isCompleted)

            if (call.request.origin.scheme != "https") {
                throw RuntimeException("Attempt to create new user over non-https connection.")
            }

            val formParameters = call.receiveParameters()
            val username = DEFAULT_USERNAME
            val password = formParameters["password"]
            val passwordConfirm = formParameters["passwordConfirm"]
            val autoStart = formParameters["autoStart"]?.toBoolean() ?: false

            val error = if (password.isNullOrBlank() || passwordConfirm.isNullOrBlank())
                "You must include a new password and confirm it."
            else if (password == defaultPasswordHash.toHexString())
                "Your password cannot be 'password'"
            else if (password != passwordConfirm)
                "Passwords don't match."
            else
                null

            if (error != null) {
                newUserForm(call, error)
            } else {
                val passwordHash = Sha256Hash.ofString(password!!)
                changeAuthorityKeyStorePassword(storagePath, defaultPasswordHash, passwordHash)
                if (autoStart) {
                    autoStart()
                }
                startingPage(call, AuthToken(username).encode(authSecretKey), migratingData)
                delay(1000)
                loginCredentials.complete(LoginCredentials(username, passwordHash))
            }
        }

        get("/changePassword") {
            require(!loginCredentials.isCompleted) { "Your password can only be change at startup." }
            changePasswordForm(call)
        }

        post("/changePassword") {
            require(!loginCredentials.isCompleted)
            val formParameters = call.receiveParameters()
            val oldPassword = formParameters["password"]
            val newPassword = formParameters["newPassword"]
            val newPasswordConfirm = formParameters["newPasswordConfirm"]
            val passwordHash = oldPassword?.let { Sha256Hash.ofString(it) }

            val error = if (oldPassword.isNullOrBlank())
                "Old password is required"
            else if (newPassword.isNullOrBlank() || newPasswordConfirm.isNullOrBlank())
                "You must include a new password and confirm it."
            else if (newPassword == "password")
                "Your password cannot be 'password'"
            else if (newPassword != newPasswordConfirm)
                "Passwords don't match."
            else if (!validateAuthorityKeyStorePassword(storagePath, passwordHash!!))
                "Old password is incorrect."
            else
                null

            if (error != null) {
                changePasswordForm(call, error)
            } else {
                changeAuthorityKeyStorePassword(
                    storagePath,
                    passwordHash!!,
                    Sha256Hash.ofString(newPassword!!)
                )
                call.respondRedirect("/start")
            }
        }

        staticResources("init", "web/init/${getOS().toString().lowercase()}")
        staticResources("img", "web/img/")
        staticResources("css", "web/css/")
        staticResources("js", "web/js")
    }
}