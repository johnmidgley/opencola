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
import io.opencola.model.Id
import io.opencola.network.providers.http.HttpNetworkProvider
import io.opencola.security.hash.Sha256Hash
import io.opencola.security.keystore.defaultPasswordHash
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry
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
import javax.crypto.SecretKey
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes
import io.opencola.application.Application as app

private fun ApplicationCall.getAuthToken(authSecretKey: SecretKey): AuthToken? {
    return sessions.get<UserSession>()?.decodeAuthToken(authSecretKey)
}

private const val DEFAULT_USERNAME = "oc"

// TODO: All routes should authenticate caller and authorize activity. Right now everything is open
fun Application.configureBootstrapRouting(
    storagePath: Path,
    serverConfig: ServerConfig,
    authSecretKey: SecretKey,
    loginCredentials: CompletableDeferred<LoginCredentials>,
) {
    val migratingData =
        storagePath.resolve("address-book.db").exists() && !storagePath.resolve("address-book").isDirectory()

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
            val passwordHashString = formParameters["password"]?.let { Sha256Hash.ofString(it).toHexString() }

            if (passwordHashString.isNullOrBlank()) {
                startupForm(call, "Please enter a password")
            } else {
                val passwordHash = Sha256Hash.fromHexString(passwordHashString)
                if (validateAuthorityKeyStorePassword(storagePath, passwordHash)) {
                    startingPage(call, AuthToken(username).encode(authSecretKey), migratingData)
                    loginCredentials.complete(LoginCredentials(username, passwordHash))
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
            val passwordHashString = formParameters["password"]?.let { Sha256Hash.ofString(it).toHexString() }
            val passwordHashConfirmString =
                formParameters["passwordConfirm"]?.let { Sha256Hash.ofString(it).toHexString() }
            val autoStart = formParameters["autoStart"]?.toBoolean() ?: false

            val error = if (passwordHashString.isNullOrBlank() || passwordHashConfirmString.isNullOrBlank())
                "You must include a new password and confirm it."
            else if (passwordHashString == defaultPasswordHash.toHexString())
                "Your password cannot be 'password'"
            else if (passwordHashString != passwordHashConfirmString)
                "Passwords don't match."
            else
                null

            if (error != null) {
                newUserForm(call, error)
            } else {
                val passwordHash = Sha256Hash.fromHexString(passwordHashString!!)
                changeAuthorityKeyStorePassword(storagePath, defaultPasswordHash, passwordHash)
                if (autoStart) {
                    autoStart()
                }
                startingPage(call, AuthToken(username).encode(authSecretKey), migratingData)
                delay(1000)
                loginCredentials.complete(LoginCredentials(username, passwordHash))
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
            val oldPasswordHashString = formParameters["oldPassword"]?.let { Sha256Hash.ofString(it).toHexString() }
            val passwordHashString = formParameters["newPassword"]?.let { Sha256Hash.ofString(it).toHexString() }
            val passwordHashConfirmString =
                formParameters["newPasswordConfirm"]?.let { Sha256Hash.ofString(it).toHexString() }
            val oldPasswordHash = oldPasswordHashString?.let { Sha256Hash.fromHexString(it) }

            val error = if (oldPasswordHashString.isNullOrBlank())
                "Old password is required"
            else if (passwordHashString.isNullOrBlank() || passwordHashConfirmString.isNullOrBlank())
                "You must include a new password and confirm it."
            else if (passwordHashString == "password")
                "Your password cannot be 'password'"
            else if (passwordHashString != passwordHashConfirmString)
                "Passwords don't match."
            else if (!validateAuthorityKeyStorePassword(storagePath, oldPasswordHash!!))
                "Old password is incorrect."
            else
                null

            if (error != null) {
                changePasswordForm(call, error)
            } else {
                changeAuthorityKeyStorePassword(
                    storagePath,
                    oldPasswordHash!!,
                    Sha256Hash.fromHexString(passwordHashString!!)
                )
                call.respondRedirect("/")
            }
        }

        staticResources("", "bootstrap/${getOS().toString().lowercase()}")
    }
}

fun Application.configureRouting(app: app, authSecretKey: SecretKey) {
    // TODO: Make and user general opencola.server
    val logger = KotlinLogging.logger("opencola.init")
    val ocServerPorts = listOfNotNull(app.config.server.port, app.config.server.ssl?.port).toSet()

    routing {
        // Authentication from https://ktor.io/docs/session-auth.html
        fun expectPersona(call: ApplicationCall): PersonaAddressBookEntry {
            val personaId = call.parameters["personaId"]?.let { Id.decode(it) }
                ?: throw IllegalArgumentException("No personaId specified")
            return app.inject<AddressBook>().getEntry(personaId, personaId) as? PersonaAddressBookEntry
                ?: throw IllegalArgumentException("Invalid personaId: $personaId")
        }

        fun getPersona(call: ApplicationCall): PersonaAddressBookEntry? {
            return call.parameters["personaId"]
                ?.let { Id.tryDecode(it) }
                ?.let { app.inject<AddressBook>().getEntry(it, it) as? PersonaAddressBookEntry }
        }

        fun getContext(call: ApplicationCall): Context {
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
            val passwordHashString = formParameters["password"]?.let { Sha256Hash.ofString(it).toHexString() }

            if (passwordHashString.isNullOrBlank()) {
                loginPage(call, "Please enter a password")
            } else if (validateAuthorityKeyStorePassword(
                    app.storagePath,
                    Sha256Hash.fromHexString(passwordHashString)
                )
            ) {
                val authToken = AuthToken(username).encode(authSecretKey)
                call.sessions.set(UserSession(authToken))
                call.respondRedirect("/")
            } else {
                loginPage(call, "Bad password")
            }
        }

        get("logout") {
            val username = call.getAuthToken(authSecretKey)?.username ?: app.config.security.login.username
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

        authenticate("auth-session") {
            get("/search") {
                val query = call.request.queryParameters["q"]
                    ?: throw IllegalArgumentException("No query (q) specified in parameters")

                val personaIds = getPersona(call)?.let { setOf(it.personaId) } ?: setOf()
                call.respond(handleSearch(app.inject(), app.inject(), personaIds, query))
            }

            get("/entity/{entityId}") {
                getEntity(call, expectPersona(call), app.inject(), app.inject(), app.inject(), app.inject())
            }

            post("/entity/{entityId}") {
                val entityId =
                    Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))
                saveEntity(
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    getContext(call),
                    expectPersona(call),
                    entityId
                )?.let {
                    call.respond(it)
                } ?: call.respond(HttpStatusCode.Unauthorized)
            }

            put("/entity/{entityId}") {
                val entityPayload = call.receive<EntityPayload>()
                updateEntity(
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    getContext(call),
                    expectPersona(call),
                    entityPayload
                )?.let {
                    call.respond(it)
                } ?: call.respond(HttpStatusCode.Unauthorized)
            }

            delete("/entity/{entityId}") {
                val entityId = call.parameters["entityId"]?.let { Id.decode(it) }
                    ?: throw IllegalArgumentException("No entityId specified")

                deleteEntity(
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    getContext(call),
                    expectPersona(call),
                    entityId
                )?.let {
                    call.respond(it)
                } ?: call.respond("{}")
            }

            post("/entity/{entityId}/comment") {
                val entityId =
                    Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))
                val comment = call.receive<PostCommentPayload>()

                updateComment(
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    getContext(call),
                    expectPersona(call),
                    entityId,
                    comment
                )?.let {
                    call.respond(it)
                }
            }

            post("/entity/{entityId}/attachment") {
                val entityId =
                    Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))
                addAttachment(
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    getContext(call),
                    expectPersona(call).entityId,
                    entityId,
                    call.receive()
                )?.let {
                    call.respond(it)
                }
            }

            delete("/entity/{entityId}/attachment/{attachmentId}") {
                val entityId =
                    Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))
                val attachmentId =
                    Id.decode(
                        call.parameters["attachmentId"] ?: throw IllegalArgumentException("No attachmentId specified")
                    )

                deleteAttachment(
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    getContext(call),
                    expectPersona(call).entityId,
                    entityId,
                    attachmentId
                )?.let {
                    call.respond(it)
                }
            }

            post("/post") {
                newPost(
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    getContext(call),
                    expectPersona(call),
                    call.receive(),
                    ocServerPorts
                )?.also {
                    call.respond(it)
                }
            }

            delete("/comment/{commentId}") {
                // TODO: Remove call and parse comment id out here, so handlers don't need to know anything about ktor
                deleteComment(call, expectPersona(call), app.inject())
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
                call.respond(
                    handleGetFeed(
                        personaIds,
                        app.inject(),
                        app.inject(),
                        app.inject(),
                        app.inject(),
                        app.inject(),
                        query
                    )
                )
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
                handlePostActionCall(call, expectPersona(call).personaId, app.inject(), app.inject(), ocServerPorts)
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

            post("/upload") {
                call.respond(
                    handleUpload(
                        app.inject(),
                        app.inject(),
                        expectPersona(call).personaId,
                        call.receiveMultipart()
                    )
                )
            }

            getResourceFilePath(
                "web",
                app.storagePath.resolve("resources"),
                !app.config.resources.allowEdit
            ).let {
                logger.info("Initializing static resources from $it")
                staticFiles("/", it.toFile())
            }
        }

        post("/networkNode") {
            app.inject<HttpNetworkProvider>().handleMessage(call.receive<ByteArray>())
            call.respond(HttpStatusCode.OK)
        }

        get("/networkNode/pk") {
            call.respondBytes(
                app.inject<HttpNetworkProvider>().publicKey.encoded,
                ContentType("application", "octet-stream")
            )
        }
    }
}
