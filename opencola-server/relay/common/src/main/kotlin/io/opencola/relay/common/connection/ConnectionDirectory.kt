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

package io.opencola.relay.common.connection

import io.opencola.model.Id
import java.net.URI

data class ConnectionEntry(
    val id: Id,
    val address: URI,
    val connection: Connection? = null,
    val connectTimeMilliseconds: Long = System.currentTimeMillis()
)

interface ConnectionDirectory {
    val localAddress: URI
    fun add(connection: Connection): ConnectionEntry
    fun get(id: Id): ConnectionEntry?
    fun getConnections(): Sequence<ConnectionEntry>
    fun remove(id: Id)
    fun size(): Long
    fun closeAll()
}