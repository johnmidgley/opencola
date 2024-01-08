package io.opencola.event.bus

interface Reactor {
    fun handleMessage(event: Event)
}

