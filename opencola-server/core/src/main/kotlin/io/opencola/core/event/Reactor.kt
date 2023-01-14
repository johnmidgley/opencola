package io.opencola.core.event

import io.opencola.core.event.EventBus.Event

interface Reactor {
    fun handleMessage(event: Event)
}

