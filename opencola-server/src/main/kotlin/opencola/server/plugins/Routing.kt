package opencola.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.Application
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.opencola.core.config.LoginConfig
import io.opencola.core.config.ServerConfig
import io.opencola.core.config.getResourceFilePath
import io.opencola.core.extensions.nullOrElse
import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import io.opencola.core.network.Notification
import io.opencola.core.network.handleGetTransactions
import io.opencola.core.network.handleNotification
import io.opencola.core.network.providers.http.HttpNetworkProvider
import io.opencola.core.security.EncryptionParams
import io.opencola.core.system.OS
import io.opencola.core.system.autoStart
import io.opencola.core.system.getOS
import io.opencola.core.system.openFile
import kotlinx.coroutines.CompletableDeferred
import mu.KotlinLogging
import opencola.server.AuthToken
import opencola.server.LoginCredentials
import opencola.server.handlers.*
import opencola.server.view.*
import java.nio.file.Path
import kotlin.io.path.readBytes
import io.opencola.core.config.Application as app

private fun ApplicationCall.getAuthToken(encryptionParams: EncryptionParams): AuthToken? {
    return sessions.get<UserSession>()?.decodeAuthToken(encryptionParams)
}

// TODO: All routes should authenticate caller and authorize activity. Right now everything is open
fun Application.configureBootstrapRouting(
    storagePath: Path,
    serverConfig: ServerConfig,
    loginConfig: LoginConfig,
    authEncryptionParams: EncryptionParams,
    loginCredentials: CompletableDeferred<LoginCredentials>,
) {

    routing {
        get("/") {
            val isNewUser = isNewUser(storagePath)

            if(serverConfig.ssl != null && !isCertInstalled(storagePath)) {
                call.respondRedirect("/installCert.html")
            } else if(isNewUser) {
                call.respondRedirect("newUser")
            } else if (call.request.origin.scheme != "https") {
                call.respondRedirect("https://localhost:${serverConfig.ssl!!.port}")
            } else {
                val username = call.getAuthToken(authEncryptionParams)?.username ?: loginConfig.username
                startupForm(call, username)
            }
        }

        post("/") {
            val formParameters = call.receiveParameters()
            val username = formParameters["username"]
            val password = formParameters["password"]

            if(username == null || username.isBlank()) {
                startupForm(call, loginConfig.username, "Please enter a username")
            }else if (password == null || password.isBlank()) {
                startupForm(call, username,"Please enter a password")
            } else {
                if (validateAuthorityKeyStorePassword(storagePath, password)) {
                    startingPage(call, AuthToken(username).encode(authEncryptionParams))
                    loginCredentials.complete(LoginCredentials(username.toString(), password.toString()))
                } else
                    startupForm(call, username, "Bad password")
            }
        }

        get("/newUser") {
            newUserForm(call, loginConfig.username)
        }

        post("/newUser") {
            val formParameters = call.receiveParameters()
            val username = formParameters["username"]
            val password = formParameters["password"]
            val passwordConfirm = formParameters["passwordConfirm"]
            val autoStart = formParameters["autoStart"]?.toBoolean() ?: false

            val error = if (username == null || username.isBlank())
              "Please enter a username"
            else if (password == null || password.isBlank() || passwordConfirm == null || password.isBlank())
                "You must include a new password and confirm it."
            else if (password == "password")
                "Your password cannot be 'password'"
            else if (password != passwordConfirm)
                "Passwords don't match."
            else
                null

            if(error != null) {
                newUserForm(call, username!!, error)
            } else {
                changeAuthorityKeyStorePassword(storagePath, "password", password!!)
                if(autoStart) { autoStart() }
                startingPage(call, AuthToken(username!!).encode(authEncryptionParams))
                loginCredentials.complete(LoginCredentials(username.toString(), password.toString()))
            }
        }

        post("/installCert") {
            // FYI - linux only supports pem. Windows and Mac support both der and pem
            val certPath = storagePath.resolve("cert/opencola-ssl.pem")
            val os = getOS()

            if(os == OS.Mac) {
                openFile(certPath)
                call.respondRedirect("installCert.html")
            } else {
                // Send the raw cert for manual installation
                call.response.header("Content-Disposition", "attachment; filename=\"opencola-ssl.pem\"")
                call.respondBytes(certPath.readBytes(), ContentType("application", "x-x509-ca-cert"))
            }
        }

        post("/certInstalled") {
            setCertInstalled(storagePath)
            call.respondRedirect("https://localhost:${serverConfig.ssl!!.port}")
        }

        get("/changePassword") {
            changePasswordForm(call)
        }

        post("/changePassword") {
            val formParameters = call.receiveParameters()
            val oldPassword = formParameters["oldPassword"]
            val password = formParameters["newPassword"]
            val passwordConfirm = formParameters["newPasswordConfirm"]

            val error = if (oldPassword == null || oldPassword.isBlank())
                "Old password is required"
            else if (password == null || password.isBlank()
                || passwordConfirm == null || passwordConfirm.isBlank())
                "You must include a new password and confirm it."
            else if (password == "password")
                "Your password cannot be 'password'"
            else if (password != passwordConfirm)
                "Passwords don't match."
            else if (!validateAuthorityKeyStorePassword(storagePath, oldPassword))
                "Old password is incorrect."
            else
                null

            if (error != null) {
                changePasswordForm(call, error)
            } else {
                changeAuthorityKeyStorePassword(storagePath, oldPassword!!, password!!)
                call.respondRedirect("/")
            }
        }

        static("") {
            resources("bootstrap/${getOS().toString().lowercase()}")
        }
    }
}

fun Application.configureRouting(app: app, authEncryptionParams: EncryptionParams) {
    // TODO: Make and user general opencola.server
    val logger = KotlinLogging.logger("opencola.init")

    routing {
        // Authentication from https://ktor.io/docs/session-auth.html

        get("/login") {
            val username = call.getAuthToken(authEncryptionParams)?.username ?: app.config.security.login.username
            loginPage(call, username)

        }

        post("/login") {
            val formParameters = call.receiveParameters()
            val username = formParameters["username"]
            val password = formParameters["password"]

            if(username == null || username.isBlank())
                loginPage(call, app.config.security.login.username, "Please enter a username")
            if(password == null || password.isBlank()) {
                loginPage(call, app.config.security.login.username, "Please enter a password")
            } else if (validateAuthorityKeyStorePassword(app.storagePath, password)) {
                val authToken = AuthToken(username!!).encode(authEncryptionParams)
                call.sessions.set(UserSession(authToken))
                call.respondRedirect("/")
            } else {
                loginPage(call, app.config.security.login.username, "Bad password")
            }
        }

        get("logout") {
            val username = call.getAuthToken(authEncryptionParams)?.username ?: app.config.security.login.username
            val authToken = AuthToken(username, -1).encode(authEncryptionParams)
            call.sessions.set(UserSession(authToken))
            call.respondRedirect("/login")
        }

        get("/isLoggedIn") {
            val authToken = call.getAuthToken(authEncryptionParams)
            if(authToken?.isValid() == true)
                call.respond(HttpStatusCode.OK)
            else
                call.respond(HttpStatusCode.Unauthorized)
        }

        authenticate("auth-session") {
            get("/search") {
                val query = call.request.queryParameters["q"]
                    ?: throw IllegalArgumentException("No query (q) specified in parameters")

                call.respond(handleSearch(app.inject(), app.inject(), app.inject(), query))
            }

            get("/entity/{entityId}") {
                // TODO: Authority should be passed (and authenticated) in header
                getEntity(call, app.inject(), app.inject(), app.inject())
            }

            post("/entity/{entityId}") {
                saveEntity(call, app.inject(), app.inject(), app.inject())
            }

            put("/entity/{entityId}") {
                updateEntity(call, app.inject(), app.inject(), app.inject())
            }

            delete("/entity/{entityId}") {
                deleteEntity(call, app.inject(), app.inject(), app.inject())
            }

            post("/entity/{entityId}/comment") {
                addComment(call, app.inject(), app.inject(), app.inject())
            }

            post("/post") {
                newPost(call, app.inject(), app.inject(), app.inject())
            }

            delete("/comment/{commentId}") {
                // TODO: Remove call and parse comment id out here, so handlers don't need to know anything about ktor
                deleteComment(call, app.inject(), app.inject())
            }

            // TODO: Think about checking for no extra parameters
            suspend fun getTransactions(call: ApplicationCall) {
                val authorityId =
                    Id.decode(call.parameters["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
                val peerId = Id.decode(call.parameters["peerId"] ?: throw IllegalArgumentException("No peerId set"))
                val transactionId = call.parameters["mostRecentTransactionId"].nullOrElse { Id.decode(it) }
                val numTransactions = call.parameters["numTransactions"].nullOrElse { it.toInt() }

                call.respond(
                    handleGetTransactions(
                        app.inject(),
                        app.inject(),
                        authorityId,
                        peerId,
                        transactionId,
                        numTransactions
                    )
                )
            }

            get("/transactions/{authorityId}") {
                getTransactions(call)
            }

            get("/transactions/{authorityId}/{mostRecentTransactionId}") {
                getTransactions(call)
            }

            get("/data/{id}") {
                val authority = app.inject<Authority>()
                handleGetDataCall(call, app.inject(), app.inject(), authority.authorityId)
            }

            get("/data/{id}/{partName}") {
                // TODO: Add a parameters extension that gets the parameter value or throws an exception
                val authority = app.inject<Authority>()
                handleGetDataPartCall(call, authority.authorityId, app.inject())
            }

            get("/actions/{uri}") {
                val authority = app.inject<Authority>()
                handleGetActionsCall(call, authority.authorityId, app.inject())
            }

            post("/notifications") {
                val notification = call.receive<Notification>()
                handleNotification(app.inject(), app.inject(), notification)
                call.respond(HttpStatusCode.OK)
            }

            get("/feed") {
                // TODO: Handle filtering of authorities
                handleGetFeed(call, app.inject(), app.inject(), app.inject(), app.inject())
            }

            get("/peers") {
                call.respond(getPeers(app.inject(), app.inject()))
            }

            // TODO: change token to inviteToken
            get("/peers/token") {
                val inviteToken =
                    getInviteToken(app.inject<Authority>().entityId, app.inject(), app.inject(), app.inject())
                call.respond(TokenRequest(inviteToken))
            }

            post("/peers/token") {
                val tokenRequest = call.receive<TokenRequest>()
                call.respond(inviteTokenToPeer(app.inject<Authority>().entityId, tokenRequest.token))
            }

            put("/peers") {
                val peer = call.receive<Peer>()
                updatePeer(app.inject<Authority>().entityId, app.inject(), app.inject(), app.inject(), peer)
                call.respond("{}")
            }

            delete("/peers/{peerId}") {
                val peerId = Id.decode(call.parameters["peerId"] ?: throw IllegalArgumentException("No id set"))
                deletePeer(app.inject(), peerId)
                call.respond("{}")
            }

            post("/action") {
                val authority = app.inject<Authority>()
                handlePostActionCall(call, authority.authorityId, app.inject(), app.inject(), app.inject())
            }

            static {
                // TODO: Resources don't need to be extracted - can serve right from resources - FIX
                val resourcePath = getResourceFilePath("web", app.storagePath.parent.resolve("resource-cache"))
                file("/", resourcePath.resolve("index.html").toString())
            }
        }

        static {
            // TODO: Resources don't need to be extracted - can serve right from resources - FIX
            val resourcePath = getResourceFilePath("web", app.storagePath.parent.resolve("resource-cache"))
            logger.info("Initializing static resources from $resourcePath")
            files(resourcePath.toString())
        }

        post("/networkNode") {
            val envelopeBytes = call.receive<ByteArray>()
            call.respondBytes(app.inject<HttpNetworkProvider>().handleMessage(envelopeBytes, useEncryption = true))
        }
    }
}
