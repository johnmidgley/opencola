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
import mu.KotlinLogging
import java.util.concurrent.locks.ReentrantLock

class MessageQueue(private val recipientId: Id, private val maxStoredBytes: Long) {
    private val logger = KotlinLogging.logger("MessageQueue")

    var bytesStored: Long = 0
        private set

    val numMessages: Int
        get() = queuedMessages.size

    // We want to be able to replace items in the message queue, if a newer message with the same senderSpecificKey.
    // To do this, without having to copy the whole list on change, we simply use a mutable list and lock it when
    // operating on it
    // TODO: Use coroutines equivalent - this will cause unnecessary contention
    private val lock = ReentrantLock()
    private val queuedMessages = mutableListOf<StoredMessage>()

    fun addMessage(bytesAvailable: Long, storedMessage: StoredMessage) {
        lock.lock()
        try {
            require(storedMessage.header.to == recipientId)
            require(storedMessage.header.storageKey.value != null)

            logger.info { "Adding message: $storedMessage" }

            val matchingMessages = queuedMessages
                .filter { it.matches(storedMessage) }
                .mapIndexed { index, matchingMessage ->
                    object {
                        val index = index
                        val matchingMessage = matchingMessage
                    }
                }

            // This slightly under-counts usage, as it only counts the bytes inside the signed message, not including
            // the signature. Fixing this would be inefficient, as we'd need to encode the signed message to get its
            // size, which isn't worth it.
            val existingMessageSize = matchingMessages.sumOf { it.matchingMessage.body.bytes.size }

            val bytesDelta = storedMessage.body.bytes.size - existingMessageSize

            if(bytesDelta > bytesAvailable) {
                logger.warn { "Message store is full - dropping message" }
                return
            }

            if (bytesStored + bytesDelta > maxStoredBytes) {
                logger.info { "Message store for $recipientId is full - dropping message" }
                return
            }

            if(matchingMessages.count() > 1) {
                logger.error { "Multiple messages with the same senderSpecificKey in the message queue for $recipientId" }
            }

            // Replace existing message from the same sender. We do this, rather than ignoring the message,
            // since newer messages with the same key may contain more recent data.
            val existingMessage = matchingMessages.firstOrNull()

            if (existingMessage != null) {
                bytesStored -= existingMessageSize
                this.queuedMessages[existingMessage.index] = storedMessage
            } else
                this.queuedMessages.add(storedMessage)

            // TODO: This is over counting TOTAL memory usage, as a single message to multiple receivers will be referenced multiple times
            bytesStored += storedMessage.body.bytes.size
        } finally {
            lock.unlock()
        }
    }

    fun getMessages(): List<StoredMessage> {
        lock.lock()
        try {
            return queuedMessages.toList()
        } finally {
            lock.unlock()
        }
    }

    fun removeMessage(header: StoredMessageHeader) {
        lock.lock()
        try {
            val matchingMessages =
                queuedMessages.filter { it.header.matches(header) }

            matchingMessages.forEach {
                logger.info { "Removing message: $it" }
            }

            queuedMessages.removeAll(matchingMessages)

            // TODO: This currently not accurate for TOTAL memory usage, as a single message to multiple receivers will be referenced multiple times
            bytesStored -= matchingMessages.sumOf { it.body.bytes.size }
        } finally {
            lock.unlock()
        }
    }

    fun removeMessages(maxAgeMilliseconds: Long, limit: Int) : List<StoredMessageHeader> {
        lock.lock()
        return try {
            queuedMessages
                .filter { it.header.timeMilliseconds < System.currentTimeMillis() - maxAgeMilliseconds }
                .take(limit)
                .map { it.header }
                .onEach { removeMessage(it) }

        } finally {
            lock.unlock()
        }
    }
}