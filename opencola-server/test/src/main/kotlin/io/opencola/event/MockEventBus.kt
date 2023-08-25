package io.opencola.event

import io.opencola.model.Id
import mu.KotlinLogging

// TODO: Make AbstractEventBus with reactor and started
class MockEventBus : EventBus {
    private var logger = KotlinLogging.logger("MockEventBus")
    private var reactor: Reactor? = DefaultReactor
    private var started = false

    object DefaultReactor : Reactor {
        private var logger = KotlinLogging.logger("DefaultReactor")
        override fun handleMessage(event: Event) {
            logger.warn { "IGNORING - handleMessage($event)" }
        }
    }

    override fun setReactor(reactor: Reactor) {
        this.reactor = reactor
    }

    override fun start() {
        if(reactor == null) throw IllegalStateException("Unable to start event bus without a reactor")
        started = true
    }

    override fun stop() {
        started = false
    }

    override fun sendMessage(name: String, data: ByteArray) {
        if(!started) throw IllegalStateException("Event bus not started")
        logger.warn { "IGNORING - MockEventBus.sendMessage($name, ${Id.ofData(data)})" }
    }
}