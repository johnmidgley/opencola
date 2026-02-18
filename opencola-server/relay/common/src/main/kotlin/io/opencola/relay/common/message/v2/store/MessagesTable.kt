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

package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.EncryptedBytes
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow

object Messages : LongIdTable() {
    val from = binary("from", 32).index()
    val to = binary("to", 32).index()
    val storageKey = binary("storageKey", 8).index()
    val secretKey = blob("secretKey")
    val dataId = binary("dataId", 32).index()
    val sizeBytes = long("sizeBytes")
    val timeMilliseconds = long("timeMilliseconds").index()
}

class MessageRow(private val resultRow: ResultRow) {
    override fun toString(): String {
        return "Message(id=$id, from=$from, to=$to, storageKey=$storageKey, secretKey=ENCRYPTED, dataId=$dataId) sizeBytes=$sizeBytes, timeMilliseconds=$timeMilliseconds)"
    }

    val id: Long by lazy { resultRow[Messages.id].value }
    val from: Id by lazy { Id(resultRow[Messages.from]) }
    val to: Id by lazy { Id(resultRow[Messages.to]) }
    val storageKey: MessageStorageKey by lazy { MessageStorageKey.ofEncoded(resultRow[Messages.storageKey]) }
    val secretKey: EncryptedBytes by lazy { EncryptedBytes.decodeProto(resultRow[Messages.secretKey].bytes) }
    val dataId: Id by lazy { Id(resultRow[Messages.dataId]) }
    val sizeBytes: Long by lazy { resultRow[Messages.sizeBytes] }
    val timeMilliseconds: Long by lazy { resultRow[Messages.timeMilliseconds] }

    fun toHeader() : StoredMessageHeader {
        return StoredMessageHeader(
            from,
            to,
            storageKey,
            secretKey,
            timeMilliseconds
        )
    }
}