package io.opencola.relay.common.policy

import io.opencola.model.Id

abstract class AbstractPolicyStore(final override val rootId: Id, protected var defaultPolicy: Policy?) : PolicyStore {
    fun initDefaultPolicy() {
        defaultPolicy = this.getPolicy(rootId, "default") ?: defaultPolicy
    }

    // This should be called by any concrete implementation when a policy is set in order to make sure the default
    // policy is updated
    fun setPolicy(policy: Policy) {
        if(policy.name == "default") {
            defaultPolicy = policy
        }
    }

    private fun expectUserPolicy(userId: Id): Policy {
        return getUserPolicy(rootId, userId) ?: throw IllegalStateException("Missing policy for user $userId")
    }

    protected fun authorizeReadPolicy(authorityId: Id) {
        if (authorityId != rootId && !expectUserPolicy(authorityId).adminPolicy.canEditPolicies) {
            throw IllegalStateException("User $authorityId cannot read policies")
        }
    }

    protected fun authorizeEditPolicies(authorityId: Id) {
        if (authorityId != rootId && !expectUserPolicy(authorityId).adminPolicy.canEditPolicies) {
            throw IllegalStateException("User $authorityId cannot edit policies")
        }
    }

    protected fun authorizeReadUserPolicy(authorityId: Id, userId: Id) {
        if (authorityId != rootId && authorityId != userId && !expectUserPolicy(authorityId).adminPolicy.canEditUserPolicies) {
            throw IllegalStateException("User $authorityId cannot read policy of user $userId")
        }
    }

    protected fun authorizeEditUserPolicies(authorityId: Id) {
        if (authorityId != rootId && !expectUserPolicy(authorityId).adminPolicy.canEditUserPolicies) {
            throw IllegalStateException("User $authorityId cannot edit user policies")
        }
    }
}