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

package io.opencola.relay.common.policy

import io.opencola.model.Id
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database

/**
 * PolicyStore backed by an exposed database
 */
class ExposedPolicyStore(
    database: Database,
    rootId: Id,
    defaultPolicy: Policy = Policy("default")
) : AbstractPolicyStore(rootId, defaultPolicy) {
    private val logger = KotlinLogging.logger("ExposedPolicyStore")
    private val userPolicyDB = PolicyDB(database)

    override fun setPolicy(authorityId: Id, policy: Policy) {
        authorizeEditPolicies(authorityId)
        userPolicyDB.upsertPolicy(authorityId, policy)
    }

    override fun getPolicy(authorityId: Id, policyName: String): Policy? {
        authorizeReadPolicy(authorityId)
        return userPolicyDB.getPolicyRow(policyName)?.policy
    }

    override fun removePolicy(authorityId: Id, policyName: String) {
        authorizeEditPolicies(authorityId)
        userPolicyDB.deletePolicy(policyName)
    }

    override fun getPolicies(authorityId: Id): Sequence<Policy> {
        authorizeReadPolicy(authorityId)
        return userPolicyDB.getPolicyRows().map { it.policy }.asSequence()
    }

    override fun setUserPolicy(authorityId: Id, userId: Id, policyName: String) {
        authorizeEditUserPolicies(authorityId)

        val policyId = userPolicyDB.getPolicyRow(policyName)?.id
            ?: throw IllegalArgumentException("No policy with name: $policyName")

        userPolicyDB.upsertUserPolicy(authorityId, userId, policyId)
    }
    override fun getUserPolicy(authorityId: Id, userId: Id): Policy? {
        authorizeReadUserPolicy(authorityId, userId)
        return userPolicyDB.getPolicyOrDefaultRow(userId)?.policy ?: defaultPolicy
    }

    override fun removeUserPolicy(authorityId: Id, userId: Id) {
        authorizeEditUserPolicies(authorityId)
        userPolicyDB.deleteUserPolicy(userId)
    }

    override fun getUserPolicies(authorityId: Id): Sequence<Pair<Id, String>> {
        authorizeEditUserPolicies(authorityId)
        val policies = userPolicyDB.getPolicyRows().associateBy { it.id }

        return userPolicyDB.getUserPolicyRows().map {
            val policy = policies[it.policyId]

            if(policy == null) {
                logger.error { "No policy found for id: ${it.policyId}" }
                Pair(it.userId, "MISSING")
            } else
                Pair(it.userId, policy.name)
        }.asSequence()
    }
}