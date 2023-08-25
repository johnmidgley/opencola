package io.opencola.event

interface EventBus {
    fun setReactor(reactor: Reactor)
    fun start()
    fun stop()
    // TODO: Add registerEvent(name: String, codec: ByteArrayCodec<out Any>)
    // TODO: Add listen(name: String, handler: (Event) -> Unit and remove Reactor
    // TODO: Add unlisten(name: String)
    // TODO: Change to sendEvent(event: Event), where Event is a wrapper around a RawEvent(name: String, data: ByteArray)
    fun sendMessage(name: String, data: ByteArray = "".toByteArray())
}