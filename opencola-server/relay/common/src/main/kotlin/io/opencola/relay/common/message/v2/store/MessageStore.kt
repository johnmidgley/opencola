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

package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes

interface MessageStore {
    fun addMessage(
        from: Id,
        to: Id,
        storageKey: MessageStorageKey,
        secretKey: EncryptedBytes,
        message: SignedBytes
    )

    // TODO: Make this pageable.
    fun getMessages(to: Id?): Sequence<StoredMessage>
    fun removeMessage(header: StoredMessageHeader)

    // TODO: This doesn't look quite right. It's a maintenance function, so maybe it should be in a different interface?
    // NOTE: Headers are returned, vs the whole message, since the body is not relevant when cleaning up old messages
    fun removeMessages(maxAgeMilliseconds: Long, limit: Int = 10): List<StoredMessageHeader>
    fun getUsage(): Sequence<Usage>

    // Convenient way to consume messages that only removes a message when the next one (or end) is accessed
    fun consumeMessages(id: Id): Sequence<StoredMessage> {
        return sequence {
            var previousMessage: StoredMessage? = null

            // The commented out do loopl handles batching (if getMessages were pagable)
//            do {
//                if (previousMessage != null) {
//                    removeMessage(previousMessage.header)
//                    previousMessage = null
//                }

                val messages = getMessages(id)

                messages.forEach {
                    if (previousMessage != null) {
                        removeMessage(previousMessage!!.header)
                        previousMessage = null
                    }
                    yield(it)
                    previousMessage = it
                }
//            } while (messages.size == batchSize)

            if (previousMessage != null) {
                removeMessage(previousMessage!!.header)
            }
        }
    }

    fun removeMessages(to: Id): List<StoredMessageHeader> {
        return consumeMessages(to).toList().map { it.header }
    }

    // IMPORTANT: This method hides the underlying removeMessages batch calls behind a sequence. As such, you need to
    // actually consume elements to trigger the removal. If you don't, the messages will never be removed.
    fun removeMessages(maxAgeMilliseconds: Long): Sequence<StoredMessageHeader> {
        return sequence {
            do {
                val messages = removeMessages(maxAgeMilliseconds, 10).also { yieldAll(it) }
            } while (messages.size == 10)
        }
    }
}