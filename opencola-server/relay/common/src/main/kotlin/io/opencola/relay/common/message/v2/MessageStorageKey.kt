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

package io.opencola.relay.common.message.v2

import io.opencola.model.Id
import io.opencola.util.Base58
import io.opencola.util.toByteArray
import java.util.*

// TODO: Should value be nullable? The MessageStorageKey itself seems to be nullable, so it doesn't seem necessary here.
class MessageStorageKey private constructor(val value: ByteArray?) {
    override fun toString(): String {
        return value?.let { Base58.encode(it) } ?: "none"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageStorageKey) return false
        if (value == null && other.value == null) return true
        if (value != null && other.value == null) return false
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    fun isEmpty() : Boolean {
        return value == null
    }

    fun encoded() : ByteArray? {
        return value
    }

    companion object {
        private const val KEY_LENGTH_IN_BYTES = 8

        private fun bytesToKeyBytes(bytes: ByteArray): ByteArray {
            return Id.ofData(bytes).encoded().copyOfRange(0, KEY_LENGTH_IN_BYTES)
        }

        val none = MessageStorageKey(null)

        fun unique(): MessageStorageKey {
            return MessageStorageKey(bytesToKeyBytes(UUID.randomUUID().toByteArray()))
        }

        fun of(bytes: ByteArray): MessageStorageKey {
            return MessageStorageKey(bytesToKeyBytes(bytes))
        }

        fun of(vararg ids: Id): MessageStorageKey {
            return MessageStorageKey(bytesToKeyBytes(ids.map { it.encoded() }.reduce { acc, bytes -> acc + bytes }))
        }

        fun of(key: String) : MessageStorageKey {
            return MessageStorageKey(bytesToKeyBytes(key.toByteArray()))
        }

        fun ofEncoded(key: ByteArray) : MessageStorageKey {
            require(key.size == KEY_LENGTH_IN_BYTES) { "Key must be $KEY_LENGTH_IN_BYTES bytes" }
            return MessageStorageKey(key)
        }
    }
}