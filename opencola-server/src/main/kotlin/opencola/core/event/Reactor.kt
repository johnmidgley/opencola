package opencola.core.event

import mu.KotlinLogging
import opencola.core.event.EventBus.*

interface Reactor {
    fun handleMessage(event: Event)
}

class MainReactor : Reactor {
    private val logger = KotlinLogging.logger("MainReactor")

    override fun handleMessage(event: Event) {
        when(Events.valueOf(event.name)){
            Events.NewTransaction -> logger.info { "New Transaction Event" }
            Events.PeerOnline -> logger.info { "Peer Online Event" }
        }
    }
}