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

package io.opencola.event.bus

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