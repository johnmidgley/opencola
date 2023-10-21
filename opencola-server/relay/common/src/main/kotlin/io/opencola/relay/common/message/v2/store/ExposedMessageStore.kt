package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes
import io.opencola.security.publicKeyFromBytes
import io.opencola.storage.filestore.ContentAddressedFileStore
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedMessageStore(
    private val database: Database,
    private val fileStore: ContentAddressedFileStore,
    private val maxStoredBytesPerConnection: Int = 1024 * 1024 * 50
) : MessageStore {
    private val logger = KotlinLogging.logger("ExposedMessageStore")

    class Messages(name: String = "Messages") : LongIdTable(name) {
        val from = binary("from", 32).index()
        val to = binary("to", 32).index()
        val storageKey = binary("storageKey", 8).index()
        val secretKey = blob("secretKey")
        val dataId = binary("dataId", 32).index()
        val sizeBytes = long("sizeBytes")
        val timeMilliseconds = long("timeMilliseconds").index()
    }

    private val messages = Messages()

    init {
        transaction(database) {
            SchemaUtils.create(messages)
        }
    }

    private fun getBytesStored(to: Id): Long {
        return transaction(database) {
            messages
                .slice(messages.sizeBytes.sum())
                .select { messages.to eq to.encoded() }
                .firstOrNull()?.get(messages.sizeBytes.sum()) ?: 0
        }
    }

    private fun getExistingMessage(
        from: Id,
        to: Id,
        storageKeyEncoded: ByteArray
    ): ResultRow? {
        return transaction(database) {
            messages
                .select { (messages.from eq from.encoded()) and (messages.to eq to.encoded()) and (messages.storageKey eq storageKeyEncoded) }
                .firstOrNull()
        }
    }

    override fun addMessage(
        from: Id,
        to: Id,
        storageKey: MessageStorageKey,
        secretKey: EncryptedBytes,
        message: SignedBytes
    ) {
        val messageStorageKeyEncoded = storageKey.encoded()
        require(messageStorageKeyEncoded != null) { "Attempt to add message with no messageStorageKey." }
        val messageEncoded = message.encodeProto()
        val messageDataId = fileStore.write(messageEncoded.inputStream())
        val bytesStored = getBytesStored(to)
        val existingMessage = getExistingMessage(from, to, messageStorageKeyEncoded)
        val existingMessageSize = existingMessage?.get(messages.sizeBytes) ?: 0

        if (bytesStored + messageEncoded.size - existingMessageSize > maxStoredBytesPerConnection) {
            logger.info { "Message store for $to is full - dropping message" }
            return
        }

        if (existingMessage == null) {
            // Add new message
            transaction(database) {
                messages.insertAndGetId {
                    it[this.from] = from.encoded()
                    it[this.to] = to.encoded()
                    it[this.storageKey] = messageStorageKeyEncoded
                    it[this.secretKey] = ExposedBlob(secretKey.encodeProto())
                    it[this.dataId] = messageDataId.encoded()
                    it[this.sizeBytes] = message.bytes.size.toLong()
                    it[this.timeMilliseconds] = System.currentTimeMillis()
                }
            }
        } else {
            // Update stored message
            transaction(database) {
                messages.update({ messages.id eq existingMessage[messages.id] }) {
                    it[this.secretKey] = ExposedBlob(secretKey.encodeProto())
                    it[this.dataId] = messageDataId.encoded()
                    it[this.sizeBytes] = message.bytes.size.toLong()
                    it[this.timeMilliseconds] = System.currentTimeMillis()
                }
            }
        }
    }

    private fun getMessageBody(id: Id): SignedBytes? {
        return fileStore.read(id)?.let { SignedBytes.decodeProto(it) }
    }

    private fun rowToString(row: ResultRow): String {
        val fromId = Id.ofPublicKey(publicKeyFromBytes(row[messages.from]))
        val toId = Id.ofPublicKey(publicKeyFromBytes(row[messages.to]))
        val storageKey = MessageStorageKey.ofEncoded(row[messages.storageKey])
        val dataId = Id.decode(row[messages.dataId])

        return "Message(id=${row[messages.id]}, from=$fromId, to=$toId, storageKey=$storageKey, secretKey=ENCRYPTED, dataId=$dataId)"
    }

    override fun getMessages(to: Id, limit: Int): List<StoredMessage> {
        val resultRows = transaction(database) {
            messages
                .select { messages.to eq to.encoded() }
                .orderBy(messages.id)
                .limit(limit)
                .toList()
        }

        return resultRows.mapNotNull {
            val messageDataId = Id.decode(it[messages.dataId])
            val messageBody = getMessageBody(messageDataId)

            if (messageBody != null) {
                StoredMessage(
                    Id.decode(it[messages.from]),
                    Id.decode(it[messages.to]),
                    MessageStorageKey.ofEncoded(it[messages.storageKey]),
                    EncryptedBytes.decodeProto(it[messages.secretKey].bytes),
                    messageBody,
                    it[messages.timeMilliseconds]
                )
            } else {
                val rowId = it[messages.id]
                logger.error { "Message data not found for message: ${rowToString(it)}" }
                transaction(database) {
                    messages.deleteWhere { messages.id eq rowId }
                }
                null
            }
        }
    }

    private fun deleteMessageFromDB(storedMessage: StoredMessage): ByteArray? {
        val encodedMessageStorageKey = storedMessage.storageKey.encoded()
        require(encodedMessageStorageKey != null) { "Attempt to remove message with no messageStorageKey." }

        // Grab dataId and delete message from DB
        return transaction(database) {
            val messageDataId = messages
                .slice(messages.dataId)
                .select {
                    (messages.from eq storedMessage.from.encoded()) and
                            (messages.to eq storedMessage.to.encoded()) and
                            (messages.storageKey eq encodedMessageStorageKey)
                }
                .firstOrNull()?.get(messages.dataId)

            messages.deleteWhere {
                (messages.from eq storedMessage.from.encoded()) and
                        (messages.to eq storedMessage.to.encoded()) and
                        (messages.storageKey eq encodedMessageStorageKey)
            }

            messageDataId
        }
    }

    // Delete a message from the file store if it is not referenced by any other message
    private fun safeDeleteFromFilestore(dataIdEncoded: ByteArray) {
        val dataStillReferenced = transaction(database) {
            messages
                .slice(messages.id)
                .select { messages.dataId eq dataIdEncoded }
                .firstOrNull() != null
        }

        if (!dataStillReferenced) {
            val dataId = Id.decode(dataIdEncoded)
            logger.info { "Deleting data: $dataId" }
            fileStore.delete(dataId)
        }
    }

    override fun removeMessage(storedMessage: StoredMessage) {
        val messageDataIdEncoded = deleteMessageFromDB(storedMessage)

        if (messageDataIdEncoded == null) {
            logger.warn { "Missing data: ${Id.ofData(storedMessage.message.encodeProto())}" }
            return
        }

        safeDeleteFromFilestore(messageDataIdEncoded)
    }
}