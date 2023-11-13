package io.opencola.relay.common.policy

import io.opencola.model.Id

abstract class AbstractPolicyStore(final override val rootId: Id, protected var defaultPolicy: Policy?) : PolicyStore {
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