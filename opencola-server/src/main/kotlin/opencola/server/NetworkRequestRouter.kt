package opencola.server

import opencola.core.event.EventBus
import opencola.core.extensions.nullOrElse
import opencola.core.model.Id
import opencola.core.network.*
import opencola.core.network.Request.Method.GET
import opencola.core.network.Request.Method.POST
import opencola.core.storage.AddressBook
import opencola.core.storage.EntityStore
import opencola.server.handlers.handleGetTransactionsCall
import opencola.server.handlers.handlePostNotification

class NetworkRequestRouter(
    addressBook: AddressBook,
    eventBus: EventBus,
    entityStore: EntityStore,
    peerRouter: PeerRouter,
) : RequestRouter(
    listOf(
        Route(
            GET,
            "/ping"
        ) { Response(200, "Pong") },
        Route(
            POST,
            "/notifications"
        ) { request ->
            val notification = request.decodeBody<PeerRouter.Notification>()
                ?: throw IllegalArgumentException("Body must contain Notification")

            handlePostNotification(addressBook, eventBus, notification)
            Response(200)
        },

        Route(
            GET,
            "/transactions"
        ) { request ->
            if(request.params == null){
                throw IllegalArgumentException("/transactions call requires parameters")
            }

            val authorityId =
                Id.decode(request.params["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
            val peerId = request.from
            val transactionId = request.params["mostRecentTransactionId"].nullOrElse { Id.decode(it) }
            val numTransactions = request.params["numTransactions"].nullOrElse { it.toInt() }

            val transactionResponse =
                handleGetTransactionsCall(
                entityStore,
                peerRouter,
                authorityId,
                peerId,
                transactionId,
                numTransactions
            )

            response(200, "OK", transactionResponse)
        }
    )
)


