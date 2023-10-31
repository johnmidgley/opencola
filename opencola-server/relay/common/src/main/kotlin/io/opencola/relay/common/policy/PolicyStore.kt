package io.opencola.relay.common.policy

import io.opencola.model.Id

interface PolicyStore {
    fun setPolicy(authorityId: Id, policyName: String, policy: Policy)
    fun getPolicy(policyName: String): Policy?
    fun setUserPolicy(authorityId: Id, userId: Id, policyName: String)
    fun getUserPolicy(userId: Id): Policy?
}