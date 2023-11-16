package io.opencola.event.log

import mu.KLogger

class EventLoggerWrapper(private val eventLogger: EventLogger, private val logger: KLogger) {
    private fun logMessageAndEvent(logMessage: (String) -> Unit, name: String, parameters: Map<String, String> = emptyMap(), msg: () -> Any?) {
        msg().toString().let {
            logMessage(it)
            eventLogger.log(name, parameters, it)
        }
    }

    fun trace(name: String, parameters: Map<String, String> = emptyMap(), msg: () -> Any?) {
        logMessageAndEvent(logger::trace, name, parameters, msg)
    }

    fun debug(name: String, parameters: Map<String, String> = emptyMap(), msg: () -> Any?) {
        logMessageAndEvent(logger::debug, name, parameters, msg)
    }

    fun info(name: String, parameters: Map<String, String> = emptyMap(), msg: () -> Any?) {
        logMessageAndEvent(logger::info, name, parameters, msg)
    }

    fun warn(name: String, parameters: Map<String, String> = emptyMap(), msg: () -> Any?) {
        logMessageAndEvent(logger::warn, name, parameters, msg)
    }

    fun error(name: String, parameters: Map<String, String> = emptyMap(), msg: () -> Any?) {
        logMessageAndEvent(logger::error, name, parameters, msg)
    }
}