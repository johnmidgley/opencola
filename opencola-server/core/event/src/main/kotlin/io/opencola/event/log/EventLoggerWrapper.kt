/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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