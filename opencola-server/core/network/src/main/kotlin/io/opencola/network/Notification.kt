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

import kotlinx.serialization.Serializable
import io.opencola.model.Id
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readInt
import io.opencola.serialization.writeInt
import java.io.InputStream
import java.io.OutputStream

enum class PeerEvent {
    Added,
    Online,
}

@Serializable
data class Notification(val peerId: Id, val event: PeerEvent)  {
    fun encode() : ByteArray {
        return Factory.encode(this)
    }

    companion object Factory : StreamSerializer<Notification> {
        override fun encode(stream: OutputStream, value: Notification) {
            Id.encode(stream, value.peerId)
            stream.writeInt(value.event.ordinal)
        }

        override fun decode(stream: InputStream): Notification {
            // TODO: Could throw exception
            return Notification(Id.decode(stream), PeerEvent.values()[stream.readInt()])
        }
    }
}