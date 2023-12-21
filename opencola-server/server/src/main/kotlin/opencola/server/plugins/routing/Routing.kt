package opencola.server.plugins.routing

import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.server.application.*
import io.ktor.server.application.Application
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opencola.application.getResourceFilePath
import io.opencola.model.Id
import io.opencola.network.providers.http.HttpNetworkProvider
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import mu.KotlinLogging
import opencola.server.handlers.*
import opencola.server.viewmodel.Persona
import io.opencola.application.Application as app

fun Application.configureRouting(app: app) {
    // TODO: Make and user general opencola.server
    val logger = KotlinLogging.logger("opencola.init")
    val ocServerPorts = listOfNotNull(app.config.server.port, app.config.server.ssl?.port).toSet()
    configureStorageRouting(app.storagePath.resolve("web"))

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

        authenticate("auth-session") {
            get("/search") {
                val query = call.request.queryParameters["q"]
                    ?: throw IllegalArgumentException("No query (q) specified in parameters")

                val personaIds = getPersona(call)?.let { setOf(it.personaId) } ?: setOf()
                call.respond(handleSearch(app.inject(), app.inject(), app.inject(), personaIds, query))
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

            post("/entity/{entityId}/like") {
                val likePayload = call.receive<LikePayload>()
                val entityId =
                    Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))
                likeEntity(
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    getContext(call),
                    expectPersona(call),
                    entityId,
                    likePayload
                )?.let {
                    call.respond(it)
                }
            }

            post("/entity/{entityId}/tags") {
                val tagsPayload = call.receive<TagsPayload>()
                val entityId =
                    Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))
                tagEntity(
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    getContext(call),
                    expectPersona(call),
                    entityId,
                    tagsPayload
                )?.let {
                    call.respond(it)
                }
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

            head("/feed") {
                // Placeholder for feed head request that can be used to poll for updates
                // Also useful to figure out if the server has started
            }

            get("/feed") {
                val personaIds = getPersona(call)?.let { setOf(it.personaId) }
                    ?: app.inject<AddressBook>()
                        .getEntries()
                        .filterIsInstance<PersonaAddressBookEntry>()
                        .map { it.personaId }.toSet()
                val query = call.request.queryParameters["q"]
                call.respond(app.handleGetFeed(personaIds, query))
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

        // Needed for login, when user is not authenticated
        staticResources("js", "web/js")

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
