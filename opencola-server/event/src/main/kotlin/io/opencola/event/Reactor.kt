package io.opencola.event

import io.opencola.event.EventBus.Event

interface Reactor {
    fun handleMessage(event: Event)
}

