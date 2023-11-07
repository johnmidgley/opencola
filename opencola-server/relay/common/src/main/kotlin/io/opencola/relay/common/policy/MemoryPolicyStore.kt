package io.opencola.relay.common.policy

import io.opencola.model.Id
import java.util.concurrent.ConcurrentHashMap

class MemoryPolicyStore(rootId: Id, defaultPolicy: Policy? = Policy("default")) : AbstractPolicyStore(rootId, defaultPolicy) {
    private val policies = ConcurrentHashMap<String, Policy>()
    private val userPolicies = ConcurrentHashMap<Id, Policy>()

    override fun setPolicy(authorityId: Id, policy: Policy) {
        authorizeEditPolicies(authorityId)
        policies[policy.name] = policy
    }

    override fun getPolicy(authorityId: Id, policyName: String): Policy? {
        authorizeReadPolicy(authorityId)
        return policies[policyName]
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

    override fun getUserPolicies(authorityId: Id): Sequence<Pair<Id, String>> {
        authorizeEditUserPolicies(authorityId)
        return userPolicies.entries.asSequence().map { Pair(it.key, it.value.name) }
    }
}