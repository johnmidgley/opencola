package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes
import io.opencola.storage.filestore.ContentAddressedFileStore
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import kotlin.sequences.Sequence

class ExposedMessageStore(
    database: Database,
    private val fileStore: ContentAddressedFileStore,
    private val maxStoredBytesPerConnection: Int = 1024 * 1024 * 50
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
        val bytesStored = messagesDB.getBytesStored(to)
        val existingMessage = messagesDB.getMessage (from, to, storageKey)
        val existingMessageSize = existingMessage?.sizeBytes ?: 0

        if (bytesStored + messageEncoded.size - existingMessageSize > maxStoredBytesPerConnection) {
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
        }
    }

    private fun getMessageBody(id: Id): SignedBytes? {
        return fileStore.read(id)?.let { SignedBytes.decodeProto(it) }
    }

    override fun getMessages(to: Id, limit: Int): List<StoredMessage> {
        val messageRows = messagesDB.getMessages(to, limit)

        return messageRows.mapNotNull {
            val messageDataId = it.dataId
            val messageBody = getMessageBody(messageDataId)

            if (messageBody != null) {
                StoredMessage(
                    it.from,
                    it.to,
                    it.storageKey,
                    it.secretKey,
                    messageBody,
                    it.timeMilliseconds
                )
            } else {
                val rowId = it.id
                logger.error { "Message data not found for message: $it" }
                messagesDB.deleteMessage(rowId)
                null
            }
        }
    }

    private fun deleteMessageFromDB(storedMessage: StoredMessage): Id? {
        return messagesDB.deleteMessage(storedMessage.from, storedMessage.to, storedMessage.storageKey)
    }

    // Delete a message from the file store if it is not referenced by any other message
    private fun safeDeleteFromFilestore(dataId: Id) {
        if (!messagesDB.isDataIdReferenced(dataId)) {
            logger.info { "Deleting data: $dataId" }
            fileStore.delete(dataId)
        }
    }

    override fun removeMessage(storedMessage: StoredMessage) {
        val messageDataId = deleteMessageFromDB(storedMessage)

        if (messageDataId == null) {
            logger.warn { "Missing data: ${Id.ofData(storedMessage.message.encodeProto())}" }
            return
        }

        safeDeleteFromFilestore(messageDataId)
    }

    override fun getUsage(): Sequence<Usage> {
        TODO("Not yet implemented")
    }
}