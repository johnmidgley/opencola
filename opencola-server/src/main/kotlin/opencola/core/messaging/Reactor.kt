package opencola.core.messaging

import opencola.core.messaging.MessageBus.*

interface Reactor {
    fun handleMessage(message: Message)
}