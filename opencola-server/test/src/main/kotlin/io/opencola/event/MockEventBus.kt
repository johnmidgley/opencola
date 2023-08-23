package io.opencola.event

import io.opencola.model.Id
import mu.KotlinLogging

class MockEventBus : EventBus {
    private var logger = KotlinLogging.logger("MockEventBus")
    private var reactor: Reactor? = null

    override fun start(reactor: Reactor) {
        this.reactor = reactor
    }

    override fun stop() {
    }

    override fun sendMessage(name: String, data: ByteArray) {
        logger.warn { "IGNORING - MockEventBus.sendMessage($name, ${Id.ofData(data)})" }
    }
}