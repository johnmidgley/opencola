package opencola.server.plugins

import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.server.application.*
import io.ktor.server.application.Application
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.opencola.application.ServerConfig
import io.opencola.application.getResourceFilePath
import io.opencola.util.nullOrElse
import io.opencola.model.Id
import io.opencola.network.handleGetTransactions
import io.opencola.network.providers.http.HttpNetworkProvider
import io.opencola.security.EncryptionParams
import io.opencola.storage.AddressBook
import io.opencola.storage.PersonaAddressBookEntry
import io.opencola.system.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import mu.KotlinLogging
import opencola.server.AuthToken
import opencola.server.LoginCredentials
import opencola.server.handlers.*
import opencola.server.view.*
import opencola.server.viewmodel.Persona
import java.nio.file.Path
import kotlin.io.path.readBytes
import io.opencola.application.Application as app

private fun ApplicationCall.getAuthToken(encryptionParams: EncryptionParams): AuthToken? {
    return sessions.get<UserSession>()?.decodeAuthToken(encryptionParams)
}

private const val DEFAULT_USERNAME = "oc"

// TODO: All routes should authenticate caller and authorize activity. Right now everything is open
fun Application.configureBootstrapRouting(
    storagePath: Path,
    serverConfig: ServerConfig,
    authEncryptionParams: EncryptionParams,
    loginCredentials: CompletableDeferred<LoginCredentials>,
) {

    routing {
        get("/") {
            val isNewUser = isNewUser(storagePath)

            if (serverConfig.ssl != null && !isCertInstalled(storagePath)) {
                call.respondRedirect("/installCert.html")
            } else if (isNewUser) {
                call.respondRedirect("newUser")
            } else if (call.request.origin.scheme != "https") {
                call.respondRedirect("https://localhost:${serverConfig.ssl!!.port}")
            } else {
                // val username = call.getAuthToken(authEncryptionParams)?.username ?: loginConfig.username
                startupForm(call)
            }
        }

        post("/") {
            val formParameters = call.receiveParameters()
            // TODO: User should be able to choose username for higher (external) security.
            //  This needs to be stored in the keystore, and a change username flow needs to be implemented.
            val username = DEFAULT_USERNAME
            val password = formParameters["password"]

            if (password.isNullOrBlank()) {
                startupForm(call,  "Please enter a password")
            } else {
                if (validateAuthorityKeyStorePassword(storagePath, password)) {
                    startingPage(call, AuthToken(username).encode(authEncryptionParams))
                    loginCredentials.complete(LoginCredentials(username, password.toString()))
                } else
                    startupForm(call, "Bad password")
            }
        }

        get("/login") {
            // A call from the toolbar may attempt to log in. If the server hasn't started, we'll end up here,
            // so we'll just redirect to the startup page.
            call.respondRedirect("/")
        }

        get("/newUser") {
            if (call.request.origin.scheme != "https") {
                call.respondRedirect("https://localhost:${serverConfig.ssl!!.port}/newUser")
            } else {
                newUserForm(call)
            }
        }

        post("/newUser") {
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
            else if (password == "password")
                "Your password cannot be 'password'"
            else if (password != passwordConfirm)
                "Passwords don't match."
            else
                null

            if (error != null) {
                newUserForm(call, error)
            } else {
                changeAuthorityKeyStorePassword(storagePath, "password", password!!)
                if (autoStart) {
                    autoStart()
                }
                startingPage(call, AuthToken(username).encode(authEncryptionParams))
                delay(1000)
                loginCredentials.complete(LoginCredentials(username, password.toString()))
            }
        }

        post("/installCert") {
            // FYI - linux only supports pem. Mac support both der and pem. Windows only supports der
            val certType = if (getOS() == OS.Windows) "der" else "pem"
            val certName = "opencola-ssl.$certType"
            val certPath = storagePath.resolve("cert/$certName")
            val os = getOS()

            if (os == OS.Mac || os == OS.Windows) {
                installTrustedCACert(storagePath)
                call.respondRedirect("/")
            } else {
                // Send the raw cert for manual installation
                call.response.header("Content-Disposition", "attachment; filename=\"$certName\"")
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
                || passwordConfirm == null || passwordConfirm.isBlank()
            )
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
        fun expectPersona(call: ApplicationCall): PersonaAddressBookEntry {
            val personaId = call.parameters["personaId"]?.let { Id.decode(it) }
                ?: throw IllegalArgumentException("No personaId specified")
            return app.inject<AddressBook>().getEntry(personaId, personaId) as? PersonaAddressBookEntry
                ?: throw IllegalArgumentException("Invalid personaId: $personaId")
        }

        fun getPersona(call: ApplicationCall): PersonaAddressBookEntry? {
            val personaId = call.parameters["personaId"]?.let { Id.decode(it) } ?: return null
            return app.inject<AddressBook>().getEntry(personaId, personaId) as? PersonaAddressBookEntry
        }

        fun getContext(call: ApplicationCall) : Context {
            return Context(call.parameters["context"])
        }

        get("/cert") {
            // FYI - linux only supports pem. Mac support both der and pem. Windows only supports der
            val certType = if (getOS() == OS.Windows) "der" else "pem"
            val certName = "opencola-ssl.$certType"
            val certPath = app.storagePath.resolve("cert/$certName")
            call.response.header("Content-Disposition", "attachment; filename=\"$certName\"")
            call.respondBytes(certPath.readBytes(), ContentType("application", "x-x509-ca-cert"))
        }

        get("/login") {
            if (call.request.origin.scheme != "https") {
                call.respondRedirect("https://${call.request.host()}:${app.config.server.ssl!!.port}/login")
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
            } else if (validateAuthorityKeyStorePassword(app.storagePath, password)) {
                val authToken = AuthToken(username).encode(authEncryptionParams)
                call.sessions.set(UserSession(authToken))
                call.respondRedirect("/")
            } else {
                loginPage(call, "Bad password")
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
            if (authToken?.isValid() == true)
                call.respond(HttpStatusCode.OK)
            else
                call.respond(HttpStatusCode.Unauthorized)
        }

        authenticate("auth-session") {
            get("/search") {
                val query = call.request.queryParameters["q"]
                    ?: throw IllegalArgumentException("No query (q) specified in parameters")

                val personaIds = getPersona(call)?.let { setOf(it.personaId) } ?: setOf()
                call.respond(handleSearch(app.inject(), app.inject(), personaIds, query))
            }

            get("/entity/{entityId}") {
                getEntity(call, expectPersona(call), app.inject(), app.inject())
            }

            post("/entity/{entityId}") {
                val entityId = Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))
                saveEntity(app.inject(), app.inject(), getContext(call), expectPersona(call), entityId)?.let {
                    call.respond(it)
                } ?: call.respond(HttpStatusCode.Unauthorized)
            }

            put("/entity/{entityId}") {
                val entityPayload = call.receive<EntityPayload>()
                updateEntity(app.inject(), app.inject(), getContext(call), expectPersona(call), entityPayload)?.let {
                    call.respond(it)
                } ?: call.respond(HttpStatusCode.Unauthorized)
            }

            delete("/entity/{entityId}") {
                val entityId = call.parameters["entityId"]?.let { Id.decode(it) }
                    ?: throw IllegalArgumentException("No entityId specified")

                deleteEntity(app.inject(), app.inject(), getContext(call), expectPersona(call), entityId)?.let {
                    call.respond(it)
                } ?: call.respond("{}")
            }

            post("/entity/{entityId}/comment") {
                val entityId = Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))
                val comment = call.receive<PostCommentPayload>()

                updateComment(app.inject(), app.inject(), getContext(call), expectPersona(call), entityId, comment)?.let {
                    call.respond(it)
                }
            }

            post("/post") {
                newPost(app.inject(), app.inject(), getContext(call), expectPersona(call), call.receive())?.also {
                    call.respond(it)
                }
            }

            delete("/comment/{commentId}") {
                // TODO: Remove call and parse comment id out here, so handlers don't need to know anything about ktor
                deleteComment(call, expectPersona(call), app.inject())
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
                handleGetDataCall(call, app.inject(), app.inject())
            }

            get("/data/{id}/{partName}") {
                // TODO: Add a parameters extension that gets the parameter value or throws an exception
                handleGetDataPartCall(call, getPersona(call)?.personaId, app.inject())
            }

            get("/actions/{uri}") {
                handleGetActionsCall(call, expectPersona(call).personaId, app.inject())
            }

            get("/feed") {
                val personaIds = getPersona(call)?.let { setOf(it.personaId) }
                    ?: app.inject<AddressBook>()
                        .getEntries()
                        .filterIsInstance<PersonaAddressBookEntry>()
                        .map { it.personaId }.toSet()
                val query = call.request.queryParameters["q"]
                call.respond(handleGetFeed(personaIds, app.inject(), app.inject(), app.inject(), query))
            }

            get("/peers") {
                call.respond(getPeers(expectPersona(call), app.inject()))
            }

            // TODO: change token to inviteToken
            get("/peers/token") {
                val inviteToken =
                    getInviteToken(expectPersona(call).personaId, app.inject(), app.inject(), app.inject())
                call.respond(TokenRequest(inviteToken))
            }

            post("/peers/token") {
                val tokenRequest = call.receive<TokenRequest>()
                call.respond(inviteTokenToPeer(app.inject(), tokenRequest.token))
            }

            put("/peers") {
                val peer = call.receive<Peer>()
                updatePeer(expectPersona(call).entityId, app.inject(), app.inject(), app.inject(), peer)
                call.respond("{}")
            }

            delete("/peers/{peerId}") {
                val peerId = Id.decode(call.parameters["peerId"] ?: throw IllegalArgumentException("No id set"))
                deletePeer(app.inject(), expectPersona(call).entityId, peerId)
                call.respond("{}")
            }

            post("/action") {
                handlePostActionCall(call, expectPersona(call).personaId, app.inject(), app.inject())
            }

            post("/personas") {
                val persona = call.receive<Persona>()
                call.respond(Created, createPersona(app.inject(), persona))
            }

            get("/personas/{id}") {
                val id = Id.decode(call.parameters["id"] ?: throw IllegalArgumentException("No id set"))
                call.respond(getPersona(app.inject(), id))
            }

            put("/personas") {
                val persona = call.receive<Persona>()
                call.respond(updatePersona(app.inject(), persona))
            }

            delete("/personas/{id}") {
                val id = Id.decode(call.parameters["id"] ?: throw IllegalArgumentException("No id set"))
                deletePersona(app.inject(), id)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/personas") {
                call.respond(getPersonas(app.inject()))
            }

            post("/upload"){
                call.respond(
                    handleUpload(
                        app.inject(),
                        app.inject(),
                        expectPersona(call).personaId,
                        call.receiveMultipart()))
            }

            static {
                val resourcePath = getResourceFilePath(
                    "web",
                    app.storagePath.resolve("resources"),
                    !app.config.resources.allowEdit
                )
                file("/", resourcePath.resolve("index.html").toString())
            }
        }

        static {
            // TODO: Resources don't need to be extracted - can serve right from resources - FIX
            val resourcePath = getResourceFilePath(
                "web",
                app.storagePath.resolve("resources"),
                !app.config.resources.allowEdit
            )
            logger.info("Initializing static resources from $resourcePath")
            files(resourcePath.toString())
        }

        post("/networkNode") {
            val envelopeBytes = call.receive<ByteArray>()
            call.respondBytes(app.inject<HttpNetworkProvider>().handleMessage(envelopeBytes, useEncryption = true))
        }
    }
}
