package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.EncryptedBytes
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction

class MessagesDB(private val database: Database) {
    init {
        transaction(database) {
            SchemaUtils.create(Messages)
        }
    }

    fun insertMessage(
        from: Id,
        to: Id,
        storageKey: MessageStorageKey,
        secretKey: EncryptedBytes,
        dataId: Id,
        sizeBytes: Long,
        timeMilliseconds: Long
    ) {
        val messageStorageKeyEncoded = storageKey.encoded()
        require(messageStorageKeyEncoded != null) { "Attempt to add message with no messageStorageKey." }

        transaction(database) {
            Messages.insert {
                it[this.from] = from.encoded()
                it[this.to] = to.encoded()
                it[this.storageKey] = messageStorageKeyEncoded
                it[this.secretKey] = ExposedBlob(secretKey.encodeProto())
                it[this.dataId] = dataId.encoded()
                it[this.sizeBytes] = sizeBytes
                it[this.timeMilliseconds] = timeMilliseconds
            }
        }
    }

    fun updateMessage(
        messageId: Long,
        secretKey: EncryptedBytes,
        dataId: Id,
        sizeBytes: Long,
        timeMilliseconds: Long = System.currentTimeMillis()
    ) {
        transaction(database) {
            Messages.update({ Messages.id eq messageId }) {
                it[this.secretKey] = ExposedBlob(secretKey.encodeProto())
                it[this.dataId] = dataId.encoded()
                it[this.sizeBytes] = sizeBytes
                it[this.timeMilliseconds] = timeMilliseconds
            }
        }
    }

    fun getMessage(
        from: Id,
        to: Id,
        storageKey: MessageStorageKey
    ): MessageRow? {
        val encodedStorageKey = storageKey.encoded()
        require(encodedStorageKey != null) { "Attempt to get message with no messageStorageKey." }

        return transaction(database) {
            Messages
                .select { (Messages.from eq from.encoded()) and (Messages.to eq to.encoded()) and (Messages.storageKey eq encodedStorageKey) }
                .firstOrNull()
                ?.let { MessageRow(it) }
        }
    }

    fun getMessages(
        to: Id,
        limit: Int
    ): List<MessageRow> {
        return transaction(database) {
            Messages
                .select { Messages.to eq to.encoded() }
                .orderBy(Messages.id)
                .limit(limit)
                .toList()
                .map { MessageRow(it) }
        }
    }

    fun deleteMessage(id: Long) {
        transaction(database) {
            Messages.deleteWhere { Messages.id eq id }
        }
    }

    fun deleteMessage(from: Id, to: Id, messageStorageKey: MessageStorageKey): Id? {
        val encodedMessageStorageKey = messageStorageKey.encoded()
        require(encodedMessageStorageKey != null) { "Attempt to delete message with no messageStorageKey." }

        return transaction(database) {
            Messages
                .slice(Messages.id, Messages.dataId)
                .select {
                    (Messages.from eq from.encoded()) and
                            (Messages.to eq to.encoded()) and
                            (Messages.storageKey eq encodedMessageStorageKey)
                }
                .firstOrNull()
                ?.let { resultRow ->
                    Messages.deleteWhere { Messages.id eq resultRow[Messages.id] }
                    Id(resultRow[Messages.dataId])
                }
        }
    }

    fun getBytesStored(to: Id): Long {
        return transaction(database) {
            Messages
                .slice(Messages.sizeBytes.sum())
                .select { Messages.to eq to.encoded() }
                .firstOrNull()?.get(Messages.sizeBytes.sum()) ?: 0
        }
    }

    fun isDataIdReferenced(dataId: Id): Boolean {
        return transaction(database) {
            Messages
                .slice(Messages.id)
                .select { Messages.dataId eq dataId.encoded() }
                .firstOrNull() != null
        }
    }
}