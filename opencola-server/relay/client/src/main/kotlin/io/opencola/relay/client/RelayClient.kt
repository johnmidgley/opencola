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

package io.opencola.relay.client

import io.opencola.relay.common.message.v2.MessageStorageKey
import java.security.PublicKey

typealias MessageHandler = suspend (from: PublicKey, message: ByteArray) -> Unit
typealias EventHandler = suspend (publicKey: PublicKey, event: RelayEvent) -> Unit

interface RelayClient {
    suspend fun open(messageHandler: MessageHandler)
    // TODO: Replace by handling control messages from server?
    suspend fun setEventHandler(eventHandler: EventHandler)
    suspend fun sendMessage(to: List<PublicKey>, key: MessageStorageKey, body: ByteArray)
    suspend fun sendMessage(to: PublicKey, key: MessageStorageKey, body: ByteArray) { sendMessage(listOf(to), key, body) }
    suspend fun close()
}