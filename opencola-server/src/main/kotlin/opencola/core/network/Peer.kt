package opencola.core.network

import opencola.core.model.Id

class Peer(val id: Id, val name: String, val host: String, var status: Status ) {
    enum class Status{
        Offline,
        Online
    }
}