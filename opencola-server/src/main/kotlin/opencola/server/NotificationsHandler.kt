package opencola.server

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import opencola.core.network.Peer
import opencola.core.network.PeerRouter
import opencola.service.EntityService

suspend fun handlePostNotifications(call: ApplicationCall, entityService: EntityService, peerRouter: PeerRouter){
    val notification = call.receive<PeerRouter.Notification>()
    val peerId = notification.peerId

    peerRouter.updateStatus(peerId, Peer.Status.Online)

    when(notification.event){
        PeerRouter.Event.NewTransactions -> entityService.requestTransactions(peerId)
    }

    call.respond(HttpStatusCode.OK)
}