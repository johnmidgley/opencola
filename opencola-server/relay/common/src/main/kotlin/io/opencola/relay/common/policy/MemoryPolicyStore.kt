package io.opencola.relay.common.policy

import io.opencola.model.Id

class MemoryPolicyStore(val defaultPolicy: Policy = Policy()) : PolicyStore {
    override fun setPolicy(authorityId: Id, policyName: String, policy: Policy) {
        throw NotImplementedError("MemoryPolicyStore does not support setPolicy")
    }

    override fun getPolicy(policyName: String): Policy? {
        throw NotImplementedError("MemoryPolicyStore does not support getPolicy")
    }

    override fun getUserPolicy(userId: Id): Policy {
        return defaultPolicy
    }

    override fun setUserPolicy(authorityId: Id, userId: Id, policyName: String) {
        throw NotImplementedError("MemoryPolicyStore does not support setUserPolicy")
    }
}