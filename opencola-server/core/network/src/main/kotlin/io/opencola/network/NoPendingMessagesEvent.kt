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

package io.opencola.network

import io.opencola.model.Id
import io.opencola.network.providers.ProviderEvent
import io.opencola.network.providers.ProviderEventType
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.net.URI
import java.nio.charset.Charset

data class NoPendingMessagesEvent(val personaId: Id, val address: URI) : ProviderEvent(ProviderEventType.NO_PENDING_MESSAGES) {
    companion object : StreamSerializer<NoPendingMessagesEvent> {
        override fun encode(stream: java.io.OutputStream, value: NoPendingMessagesEvent) {
            Id.encode(stream, value.personaId)
            stream.writeByteArray(value.address.toString().toByteArray())
        }

        override fun decode(stream: java.io.InputStream): NoPendingMessagesEvent {
            val personaId = Id.decode(stream)
            val address = URI(stream.readByteArray().toString(Charset.defaultCharset()))
            return NoPendingMessagesEvent(personaId, address)
        }
    }

    fun encode() : ByteArray {
        return encode(this)
    }
}