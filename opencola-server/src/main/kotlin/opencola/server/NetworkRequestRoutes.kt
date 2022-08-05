package opencola.server

import opencola.core.config.Application
import opencola.core.extensions.nullOrElse
import opencola.core.model.Id
import opencola.core.network.*
import opencola.core.network.NetworkNode.Notification
import opencola.core.network.Request.Method.GET
import opencola.core.network.Request.Method.POST
import opencola.server.handlers.handleGetTransactionsCall
import opencola.server.handlers.handlePostNotification

fun setNetworkRouting(app: Application) {
    val requestRouter = app.inject<RequestRouter>()

    requestRouter.routes = listOf(
        Route(
            GET,
            "/ping"
        ) { Response(200, "Pong") },
        Route(
            POST,
            "/notifications"
        ) { request ->
            val notification = request.decodeBody<Notification>()
                ?: throw IllegalArgumentException("Body must contain Notification")

            handlePostNotification(app.inject(), app.inject(), notification)
            Response(200)
        },

        Route(
            GET,
            "/transactions"
        ) { request ->
            if (request.params == null) {
                throw IllegalArgumentException("/transactions call requires parameters")
            }

            val authorityId =
                Id.decode(request.params["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
            val peerId = request.from
            val transactionId = request.params["mostRecentTransactionId"].nullOrElse { Id.decode(it) }
            val numTransactions = request.params["numTransactions"].nullOrElse { it.toInt() }


            val transactionResponse =
                handleGetTransactionsCall(
                    app.inject(),
                    app.inject(),
                    app.inject(),
                    authorityId,
                    peerId,
                    transactionId,
                    numTransactions
                )

            response(200, "OK", transactionResponse)
        }
    )
}


