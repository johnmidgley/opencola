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

package io.opencola.relay.common.policy

import io.opencola.model.Id
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow

/**
 * Exposed UserPolicies table definition that associates policies to users
 */
object UserPolicies : LongIdTable() {
    val authorityId = binary("authorityId", 32)
    val userId = binary("userId", 32).uniqueIndex()
    // In SQLite, at least, onDelete = ReferenceOption.RESTRICT did not stop deletion of a reference policy,
    // so we handle manually.
    val policyId = long("policyId").references(Policies.id)

    val editTimeMilliseconds = long("editTimeMilliseconds")
}

/**
 * Exposed ResultRow wrapper that converts to model level types
 */
class UserPolicyRow(private val resultRow: ResultRow) {
    val id: Long by lazy { resultRow[UserPolicies.id].value }
    val authorityId: Id by lazy { Id(resultRow[UserPolicies.authorityId]) }
    val userId: Id by lazy { Id(resultRow[UserPolicies.userId]) }
    val policyId: Long by lazy { resultRow[UserPolicies.policyId] }
    val editTimeMilliseconds: Long by lazy { resultRow[UserPolicies.editTimeMilliseconds] }
}