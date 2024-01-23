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
import java.util.concurrent.ConcurrentHashMap

class MemoryPolicyStore(rootId: Id, defaultPolicy: Policy? = Policy("default")) : AbstractPolicyStore(rootId, defaultPolicy) {
    private val policies = ConcurrentHashMap<String, Policy>()
    private val userPolicies = ConcurrentHashMap<Id, Policy>()

    init {
        this.defaultPolicy = defaultPolicy
    }

    override fun setPolicy(authorityId: Id, policy: Policy) {
        authorizeEditPolicies(authorityId)
        policies[policy.name] = policy

        if(policy.name == "default")
            defaultPolicy = policy
    }

    override fun getPolicy(authorityId: Id, policyName: String): Policy? {
        authorizeReadPolicy(authorityId)
        return policies[policyName]
    }

    override fun removePolicy(authorityId: Id, policyName: String) {
        authorizeEditPolicies(authorityId)
        policies.remove(policyName)
    }

    override fun getPolicies(authorityId: Id): Sequence<Policy> {
        authorizeReadPolicy(authorityId)
        return policies.values.asSequence()
    }

    override fun setUserPolicy(authorityId: Id, userId: Id, policyName: String) {
        authorizeEditUserPolicies(authorityId)
        val policy = policies[policyName] ?: throw IllegalArgumentException("No policy with name: $policyName")
        userPolicies[userId] = policy
    }

    override fun getUserPolicy(authorityId: Id, userId: Id): Policy? {
        authorizeReadUserPolicy(authorityId, userId)
        return userPolicies[userId] ?: defaultPolicy
    }

    override fun removeUserPolicy(authorityId: Id, userId: Id) {
        authorizeEditUserPolicies(authorityId)
        userPolicies.remove(userId)
    }

    override fun getUserPolicies(authorityId: Id): Sequence<Pair<Id, String>> {
        authorizeEditUserPolicies(authorityId)
        return userPolicies.entries.asSequence().map { Pair(it.key, it.value.name) }
    }
}