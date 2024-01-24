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

package io.opencola.event

import io.opencola.event.bus.Event
import io.opencola.event.bus.EventBus
import io.opencola.event.bus.Reactor
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