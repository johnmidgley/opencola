package io.opencola.relay.common.policy

import io.opencola.model.Id
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database

class ExposedPolicyStore(
    database: Database,
    rootId: Id,
    defaultPolicy: Policy = Policy("default")
) : AbstractPolicyStore(rootId, defaultPolicy) {
    private val logger = KotlinLogging.logger("ExposedPolicyStore")
    private val userPolicyDB = UserPolicyDB(database)

    init {
        initDefaultPolicy()
    }

    override fun setPolicy(authorityId: Id, policy: Policy) {
        authorizeEditPolicies(authorityId)
        userPolicyDB.upsertPolicy(authorityId, policy.name, policy)
        setPolicy(policy)
    }

    override fun getPolicy(authorityId: Id, policyName: String): Policy? {
        authorizeReadPolicy(authorityId)
        return userPolicyDB.getPolicyRow(policyName)?.policy
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
        return userPolicyDB
            .getUserPolicyRow(userId)
            ?.policyId
            ?.let { userPolicyDB.getPolicyRow(it)?.policy }
            ?: defaultPolicy
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