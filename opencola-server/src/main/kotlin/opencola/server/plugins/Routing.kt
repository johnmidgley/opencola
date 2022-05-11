package opencola.server.plugins

import io.ktor.routing.*
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.content.*
import io.ktor.request.*
import mu.KotlinLogging
import opencola.core.content.TextExtractor
import opencola.core.event.EventBus
import opencola.core.model.Authority
import opencola.core.network.PeerRouter
import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore
import opencola.core.storage.FileStore
import opencola.core.storage.MhtCache
import opencola.server.handlers.*
import opencola.service.EntityResult
import opencola.service.search.SearchService
import org.kodein.di.instance
import opencola.core.config.Application as app

// TODO: All routes should authenticate caller and authorize activity. Right now everything is open
fun Application.configureRouting(application: app) {
    val injector = application.injector
    // TODO: Make and user general opencola.server
    val logger = KotlinLogging.logger("opencola.init")

    routing {
        get("/search"){
            val searchService by injector.instance<SearchService>()
            handleGetSearchCall(call, searchService)
        }

        get("/entity/{entityId}"){
            // TODO: Authority should be passed (and authenticated) in header
            val authority by injector.instance<Authority>()
            val entityStore by injector.instance<EntityStore>()
            val peerRouter by injector.instance<PeerRouter>()
            getEntity(call, authority, entityStore, peerRouter)
        }

        post("/entity/{entityId}"){
            val authority by injector.instance<Authority>()
            val entityStore by injector.instance<EntityStore>()
            updateEntity(call, authority.authorityId, entityStore)
        }

        delete("/entity/{entityId}") {
            val authority by injector.instance<Authority>()
            val entityStore by injector.instance<EntityStore>()
            deleteEntity(call, authority.authorityId, entityStore)
        }

        post ("/entity/{entityId}/comment"){
            val authority by injector.instance<Authority>()
            val entityStore by injector.instance<EntityStore>()
            val peerRouter by injector.instance<PeerRouter>()
            addComment(call, authority, entityStore, peerRouter)
        }

        get("/transactions/{authorityId}"){
            val entityStore by injector.instance<EntityStore>()
            val peerRouter by injector.instance<PeerRouter>()
            handleGetTransactionsCall(call, entityStore, peerRouter)
        }

        get("/transactions/{authorityId}/{mostRecentTransactionId}"){
            val entityStore by injector.instance<EntityStore>()
            val peerRouter by injector.instance<PeerRouter>()
            handleGetTransactionsCall(call, entityStore, peerRouter)
        }

        get("/data/{id}"){
            val authority by injector.instance<Authority>()
            val mhtCache by injector.instance<MhtCache>()
            handleGetDataCall(call, mhtCache, authority.authorityId)
        }

        get("/data/{id}/{partName}"){
            // TODO: Add a parameters extension that gets the parameter value or throws an exception
            val authority by injector.instance<Authority>()
            val mhtCache by injector.instance<MhtCache>()
            handleGetDataPartCall(call, authority.authorityId, mhtCache)
        }

        post("/action"){
            val authority by injector.instance<Authority>()
            val entityStore by injector.instance<EntityStore>()
            val fileStore by injector.instance<FileStore>()
            val textExtractor by injector.instance<TextExtractor>()
            handlePostActionCall(call, authority.authorityId, entityStore, fileStore, textExtractor)
        }

        get("/actions/{uri}"){
            val authority by injector.instance<Authority>()
            val entityStore by injector.instance<EntityStore>()
            handleGetActionsCall(call, authority.authorityId, entityStore)
        }

        post("/notifications"){
            val eventBus by injector.instance<EventBus>()
            handlePostNotifications(call, eventBus)
        }

        get("/feed"){
            // TODO: Handle filtering of authorities
            val authority by injector.instance<Authority>()
            val entityStore by injector.instance<EntityStore>()
            val searchIndex by injector.instance<SearchIndex>()
            val peerRouter by injector.instance<PeerRouter>() // TODO: Should really be general address book, or from entity store
            handleGetFeed(call, authority, entityStore, searchIndex, peerRouter)
        }

        static(""){
            val resourcePath = application.applicationPath.resolve("resources")
            logger.info("Initializing static resources from $resourcePath")
            file("/", resourcePath.resolve("index.html").toString())
            files(resourcePath.toString())
        }
    }
}
