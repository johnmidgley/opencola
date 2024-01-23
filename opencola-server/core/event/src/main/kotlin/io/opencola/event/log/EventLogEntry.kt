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

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class EventLogEntry(
    val id: String,
    val name: String,
    val timeMilliseconds: Long,
    val parameters: Map<String, String> = emptyMap(),
    val message: String? = null,
) {
    constructor(name: String, parameters: Map<String, String> = emptyMap(), message: String? = null) : this(
        UUID.randomUUID().toString(),
        name,
        System.currentTimeMillis(),
        parameters,
        message,
    )

    val uuid: UUID
        get() = UUID.fromString(id)
}