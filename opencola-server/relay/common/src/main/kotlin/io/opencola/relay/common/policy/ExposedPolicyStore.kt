package io.opencola.relay.common.policy

import io.opencola.model.Id
import org.jetbrains.exposed.sql.Database

class ExposedPolicyStore(
    database: Database,
    private val rootId: Id,
    private val defaultPolicy: Policy? = Policy()
) : PolicyStore {
    private val userPolicyDB = UserPolicyDB(database)

    private fun expectUserPolicy(userId: Id): Policy {
        return getUserPolicy(userId) ?: throw IllegalStateException("Missing policy for user $userId")
    }

    override fun setPolicy(authorityId: Id, policyName: String, policy: Policy) {
        if (authorityId != rootId && !expectUserPolicy(authorityId).adminPolicy.canEditPolicies) {
            throw IllegalStateException("User $authorityId cannot edit policies")
        }

        userPolicyDB.upsertPolicy(authorityId, policyName, policy)
    }

    override fun getPolicy(policyName: String): Policy? {
        return userPolicyDB.getPolicyRow(policyName)?.policy
    }

    override fun getUserPolicy(userId: Id): Policy? {
        return userPolicyDB
            .getUserPolicyRow(userId)
            ?.policyId
            ?.let { userPolicyDB.getPolicyRow(it)?.policy }
            ?: defaultPolicy
    }

    override fun setUserPolicy(authorityId: Id, userId: Id, policyName: String) {
        if (authorityId != rootId && !expectUserPolicy(authorityId).adminPolicy.canEditUserPolicies) {
            throw IllegalStateException("User $authorityId cannot edit user policies")
        }

        val policyId = userPolicyDB.getPolicyRow(policyName)?.id
            ?: throw IllegalArgumentException("No policy with name: $policyName")

        userPolicyDB.upsertUserPolicy(authorityId, userId, policyId)
    }
}