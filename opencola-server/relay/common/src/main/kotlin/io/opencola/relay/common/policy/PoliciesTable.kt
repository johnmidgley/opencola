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

import io.opencola.model.Id
import io.opencola.relay.common.policy.Policy
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import java.nio.charset.Charset

/**
 * Exposed Policies table definition. Policies are associated with users in the UserPolicy table.
 */
object Policies : LongIdTable() {
    val authorityId = binary("authorityId", 32)
    val name = varchar("name", 255).uniqueIndex()
    val policy = blob("policy")
    val editTimeMilliseconds = long("editTimeMilliseconds")
}

/**
 * Exposed ResultRow wrapper that converts to model level types
 */
class PolicyRow(private val resultRow: ResultRow) {
    val id: Long by lazy { resultRow[Policies.id].value }
    val authorityId: Id by lazy { Id(resultRow[Policies.authorityId]) }
    val name: String by lazy { resultRow[Policies.name] }
    val policy: Policy by lazy { Json.decodeFromString(resultRow[Policies.policy].bytes.toString(Charset.defaultCharset())) }
    val editTimeMilliseconds: Long by lazy { resultRow[Policies.editTimeMilliseconds] }
}