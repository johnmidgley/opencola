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
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes
import io.opencola.storage.filestore.ContentAddressedFileStore
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import kotlin.sequences.Sequence

class ExposedMessageStore(
    database: Database,
    private val maxStoredBytes: Long,
    private val fileStore: ContentAddressedFileStore,
    private val policyStore: PolicyStore,
) : MessageStore {
    private val logger = KotlinLogging.logger("ExposedMessageStore")
    private val messagesDB = MessagesDB(database)

    override fun addMessage(
        from: Id,
        to: Id,
        storageKey: MessageStorageKey,
        secretKey: EncryptedBytes,
        message: SignedBytes
    ) {
        require(!storageKey.isEmpty()) { "Attempt to add message with no messageStorageKey." }
        val messageEncoded = message.encodeProto()
        val messageDataId = fileStore.write(messageEncoded.inputStream())
        val existingMessage = messagesDB.getMessage(from, to, storageKey)
        val existingMessageSize = existingMessage?.sizeBytes ?: 0

        val storagePolicy = policyStore.getUserPolicy(to, to)?.storagePolicy

        if (storagePolicy == null) {
            logger.warn { "No storage policy for $to - dropping message" }
            return
        }

        val bytesDelta = messageEncoded.size - existingMessageSize

        if (messagesDB.getBytesStored() + bytesDelta > maxStoredBytes) {
            logger.warn { "Message store is full - dropping message" }
            return
        }

        if (messagesDB.getBytesStored(to) + bytesDelta > storagePolicy.maxStoredBytes) {
            logger.info { "Message store for $to is full - dropping message" }
            return
        }

        if (existingMessage == null) {
            messagesDB.insertMessage(
                from,
                to,
                storageKey,
                secretKey,
                messageDataId,
                messageEncoded.size.toLong(),
                System.currentTimeMillis()
            )
        } else {
            messagesDB.updateMessage(
                existingMessage.id,
                secretKey,
                messageDataId,
                messageEncoded.size.toLong()
            )
            safeDeleteFromFilestore(existingMessage.dataId)
        }

        logger.info { "Added message: from=$from, to=$to" }
    }

    private fun getMessageBody(id: Id): SignedBytes? {
        return fileStore.read(id)?.let { SignedBytes.decodeProto(it) }
    }

    override fun getMessages(to: Id?): Sequence<StoredMessage> {
        val messageRows = messagesDB.getMessages(to)

        return sequence {
            messageRows.forEach {
                val messageDataId = it.dataId
                val messageBody = getMessageBody(messageDataId)

                if (messageBody != null) {
                    yield(StoredMessage(
                        it.from,
                        it.to,
                        it.storageKey,
                        it.secretKey,
                        messageBody,
                        it.timeMilliseconds
                    ))
                } else {
                    val rowId = it.id
                    logger.error { "Message data not found for message: $it" }
                    messagesDB.deleteMessage(rowId)
                }
            }
        }
    }

    private fun deleteMessageFromDB(header: StoredMessageHeader): Id? {
        return messagesDB.deleteMessage(header.from, header.to, header.storageKey)
    }

    // Delete a message from the file store if it is not referenced by any other message
    private fun safeDeleteFromFilestore(dataId: Id) {
        if (!messagesDB.isDataIdReferenced(dataId)) {
            logger.info { "Deleting data: $dataId" }
            fileStore.delete(dataId)
        }
    }

    override fun removeMessage(header: StoredMessageHeader) {
        logger.info { "Removing message: $header" }
        val messageDataId = deleteMessageFromDB(header)

        if (messageDataId == null) {
            logger.warn { "Missing data: $header" }
            return
        }

        safeDeleteFromFilestore(messageDataId)
    }

    override fun removeMessages(maxAgeMilliseconds: Long, limit: Int): List<StoredMessageHeader> {
        val headers = messagesDB.getMessagesOlderThan(maxAgeMilliseconds, limit).map { it.toHeader() }

        headers.forEach { removeMessage(it) }

        return headers
    }

    override fun getUsage(): Sequence<Usage> {
        return messagesDB.getToIds().asSequence().map { messagesDB.getToUsage(it) }
    }
}