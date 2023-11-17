package io.opencola.relay.common.policy

import io.opencola.model.Id

interface PolicyStore {
    val rootId: Id

    fun setPolicy(authorityId: Id, policy: Policy)
    fun getPolicy(authorityId: Id, policyName: String): Policy?
    fun getPolicies(authorityId: Id): Sequence<Policy>
    fun removePolicy(authorityId: Id, policyName: String)

    fun setUserPolicy(authorityId: Id, userId: Id, policyName: String)
    fun getUserPolicy(authorityId: Id, userId: Id): Policy?
    fun getUserPolicies(authorityId: Id): Sequence<Pair<Id, String>>
    fun removeUserPolicy(authorityId: Id, userId: Id)
}