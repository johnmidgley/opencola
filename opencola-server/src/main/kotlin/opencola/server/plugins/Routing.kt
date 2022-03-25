package opencola.server.plugins

import io.ktor.routing.*
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.content.*
import mu.KotlinLogging
import opencola.core.model.Authority
import opencola.core.network.PeerRouter
import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore
import opencola.core.storage.MhtCache
import opencola.server.*
import opencola.service.EntityService
import opencola.service.search.SearchService
import org.kodein.di.instance
import kotlin.io.path.Path
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
            handleGetEntityCall(call, authority.authorityId, entityStore)
        }

        get("/entity/{authorityId}/{entityId}"){
            val entityStore by injector.instance<EntityStore>()
            handleGetEntityCall(call, entityStore)
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
            val entityService by injector.instance<EntityService>()
            handlePostActionCall(call, entityService)
        }

        get("/actions/{uri}"){
            val authority by injector.instance<Authority>()
            val entityStore by injector.instance<EntityStore>()
            handleGetActionsCall(call, authority.authorityId, entityStore)
        }

        post("/notifications"){
            val entityService by injector.instance<EntityService>()
            val peerRouter by injector.instance<PeerRouter>()
            handlePostNotifications(call, entityService, peerRouter)
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
            logger.info("Initializing static resources from ${Path(System.getProperty("user.dir"))} ")
            file("/", "resources/index.html")
            files("resources")
        }
    }
}
