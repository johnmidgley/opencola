package io.opencola.event

interface EventBus {
    fun start(reactor: Reactor)
    fun stop()
    fun sendMessage(name: String, data: ByteArray = "".toByteArray())
}