package io.opencola.event

interface Reactor {
    fun handleMessage(event: Event)
}

