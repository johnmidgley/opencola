package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.Recipient
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
import java.security.PublicKey
import kotlin.sequences.Sequence

class ExposedMessageStore(
    private val database: Database,
    private val fileStore: ContentAddressedFileStore,
    private val maxStoredBytesPerConnection: Int = 1024 * 1024 * 50
) : MessageStore {
    private val logger = KotlinLogging.logger("ExposedMessageStore")

    class Messages(name: String = "Messages") :
        LongIdTable(name) {
        val from =
            binary("from", 128).index() // TODO: Switch to Id instead of public key - would affect whole relay server
        val to = binary("to", 128).index()
        val messageStorageKey = binary("messageStorageKey", 8)
        val messageSecretKey = blob("messageSecretKey")
        val messageDataId = binary("messageDataId", 32).index() // TODO: Is index necessary?
        val sizeBytes = long("sizeBytes")
        val timeMilliseconds = long("timeMilliseconds").index()
    }

    private val messages = Messages()

    init {
        transaction(database) {
            SchemaUtils.create(messages)
        }
    }

    private fun getBytesStored(toEncoded: ByteArray): Long {
        return transaction(database) {
            messages
                .slice(messages.sizeBytes.sum())
                .select { messages.to eq toEncoded }
                .firstOrNull()?.get(messages.sizeBytes.sum()) ?: 0
        }
    }

    private fun getExistingMessage(
        fromEncoded: ByteArray,
        toEncoded: ByteArray,
        messageStorageKeyEncode: ByteArray
    ): ResultRow? {
        return transaction(database) {
            messages
                .select { (messages.from eq fromEncoded) and (messages.to eq toEncoded) and (messages.messageStorageKey eq messageStorageKeyEncode) }
                .firstOrNull()
        }
    }

    override fun addMessage(
        from: PublicKey,
        to: Recipient,
        messageStorageKey: MessageStorageKey,
        message: SignedBytes
    ) {
        val messageStorageKeyEncoded = messageStorageKey.encoded()
        require(messageStorageKeyEncoded != null) { "Attempt to add message with no messageStorageKey." }
        val messageEncoded = message.encodeProto()
        val messageDataId = fileStore.write(messageEncoded.inputStream())
        val fromEncoded = from.encoded
        val toEncoded = to.publicKey.encoded
        val bytesStored = getBytesStored(toEncoded)
        val existingMessage = getExistingMessage(fromEncoded, toEncoded, messageStorageKeyEncoded)
        val existingMessageSize = existingMessage?.get(messages.sizeBytes) ?: 0

        if (bytesStored + messageEncoded.size - existingMessageSize > maxStoredBytesPerConnection) {
            logger.info { "Message store for ${Id.ofPublicKey(from)} is full - dropping message" }
            return
        }

        if (existingMessage == null) {
            // Add new message
            transaction(database) {
                messages.insertAndGetId {
                    it[this.from] = fromEncoded
                    it[this.to] = toEncoded
                    it[this.messageStorageKey] = messageStorageKeyEncoded
                    it[this.messageSecretKey] = ExposedBlob(to.messageSecretKey.encodeProto())
                    it[this.messageDataId] = messageDataId.encoded()
                    it[this.sizeBytes] = message.bytes.size.toLong()
                    it[this.timeMilliseconds] = System.currentTimeMillis()
                }
            }
        } else {
            // Update stored message
            transaction(database) {
                messages.update({ messages.id eq existingMessage[messages.id] }) {
                    it[this.messageSecretKey] = ExposedBlob(to.messageSecretKey.encodeProto())
                    it[this.messageDataId] = messageDataId.encoded()
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
        val messageStorageKey = MessageStorageKey.ofEncoded(row[messages.messageStorageKey])
        val messageDataId = Id.decode(row[messages.messageDataId])

        return "Message(id=${row[messages.id]}, from=$fromId, to=$toId, messageStorageKey=$messageStorageKey, messageDataId=$messageDataId)"
    }

    override fun getMessages(to: PublicKey): Sequence<StoredMessage> {
        // TODO: Paging
        val resultRows = transaction(database) {
            messages
                .select { messages.to eq to.encoded }
                .orderBy(messages.id)
                .toList()
        }

        return resultRows.mapNotNull {
            val messageDataId = Id.decode(it[messages.messageDataId])
            val messageBody = getMessageBody(messageDataId)

            if (messageBody != null) {
                StoredMessage(
                    publicKeyFromBytes(it[messages.from]),
                    Recipient(
                        publicKeyFromBytes(it[messages.to]),
                        EncryptedBytes.decodeProto(it[messages.messageSecretKey].bytes)
                    ),
                    MessageStorageKey.ofEncoded(it[messages.messageStorageKey]),
                    messageBody
                )
            } else {
                val rowId = it[messages.id]
                logger.error { "Message data not found for message: ${rowToString(it)}" }
                transaction(database) {
                    messages.deleteWhere { messages.id eq rowId }
                }
                null
            }
        }.asSequence()
    }

    private fun deleteMessageFromDB(storedMessage: StoredMessage) : ByteArray? {
        val encodedMessageStorageKey = storedMessage.messageStorageKey.encoded()
        require(encodedMessageStorageKey != null) { "Attempt to remove message with no messageStorageKey." }

        // Grab dataId and delete message from DB
        return transaction(database) {
            val messageDataId = messages
                .slice(messages.messageDataId)
                .select {
                    (messages.from eq storedMessage.from.encoded) and
                            (messages.to eq storedMessage.to.publicKey.encoded) and
                            (messages.messageStorageKey eq encodedMessageStorageKey)
                }
                .firstOrNull()?.get(messages.messageDataId)

            messages.deleteWhere {
                (messages.from eq storedMessage.from.encoded) and
                        (messages.to eq storedMessage.to.publicKey.encoded) and
                        (messages.messageStorageKey eq encodedMessageStorageKey)
            }

            messageDataId
        }
    }

    // Delete a message from the file store if it is not referenced by any other message
    private fun safeDeleteFromFilestore(dataIdEncoded: ByteArray) {
        val dataStillReferenced = transaction(database) {
            messages
                .slice(messages.id)
                .select { messages.messageDataId eq dataIdEncoded }
                .firstOrNull() != null
        }

        if(!dataStillReferenced) {
            val dataId = Id.decode(dataIdEncoded)
            logger.info { "Deleting data: $dataId" }
            fileStore.delete(dataId)
        }
    }

    override fun removeMessage(storedMessage: StoredMessage) {
        val messageDataIdEncoded = deleteMessageFromDB(storedMessage)

        if(messageDataIdEncoded == null) {
            logger.warn { "Missing data: ${Id.ofData(storedMessage.message.encodeProto())}" }
            return
        }

        safeDeleteFromFilestore(messageDataIdEncoded)
    }
}