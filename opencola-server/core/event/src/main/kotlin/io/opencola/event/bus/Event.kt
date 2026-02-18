/*
 * Copyright 2024-2026 OpenCola
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

import java.time.Instant

// TODO: Rework the events with a consistent Protobuf representation.
class Event(
    val id: Long,
    val name: String,
    val data: ByteArray,
    val attempt: Int = 0,
    val epochSecond: Long = Instant.now().epochSecond,
) {
    override fun toString(): String {
        return "Message(id=${this.id}, name=${this.name}, data=${data.size} bytes, attempt=$attempt, epochSecond=$epochSecond)"
    }
}