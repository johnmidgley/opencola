package io.opencola.relay.common.policy

import Policies
import PolicyRow
import io.opencola.model.Id
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction

class UserPolicyDB(private val database: Database) {
    init {
        transaction(database) {
            SchemaUtils.create(Policies)
            SchemaUtils.create(UserPolicies)
        }
    }

    private fun getUserPolicyJoinByUserId(userId: Id) : ResultRow? {
        return transaction(database) {
            UserPolicies.join(Policies, JoinType.INNER, UserPolicies.policyId, Policies.id)
                .select { UserPolicies.userId eq userId.encoded() }
                .singleOrNull()
        }
    }

    fun insertPolicy(
        authorityId: Id,
        name: String,
        policy: Policy,
        editTimeMilliseconds: Long = System.currentTimeMillis()
    ) : Long {
        return transaction(database) {
            Policies.insert {
                it[Policies.authorityId] = authorityId.encoded()
                it[Policies.name] = name
                it[Policies.policy] = ExposedBlob(Json.encodeToString(policy).toByteArray())
                it[Policies.editTimeMilliseconds] = editTimeMilliseconds
            } [Policies.id].value
        }
    }

    private fun selectPolicy(op: Op<Boolean>) : PolicyRow? {
        return transaction(database) {
            Policies
                .select { op }
                .singleOrNull()
                ?.let { PolicyRow(it) }
        }
    }

    fun getPolicyRow(
        name: String
    ) : PolicyRow? {
        return selectPolicy(Policies.name eq name)
    }

    fun getPolicyRow(
        id: Long
    ) : PolicyRow? {
        return selectPolicy(Policies.id eq id)
    }

    fun getPolicyRow(
        userId: Id
    ): PolicyRow? {
        return getUserPolicyJoinByUserId(userId)?.let { PolicyRow(it) }
    }

    fun updatePolicy(
        authorityId: Id,
        name: String,
        policy: Policy,
        editTimeMilliseconds: Long = System.currentTimeMillis()
    ) {
        transaction(database) {
            Policies.update({ Policies.name eq name }) {
                it[Policies.authorityId] = authorityId.encoded()
                it[Policies.name] = name
                it[Policies.policy] = ExposedBlob(Json.encodeToString(policy).toByteArray())
                it[Policies.editTimeMilliseconds] = editTimeMilliseconds
            }
        }
    }

    fun upsertPolicy(
        authorityId: Id,
        name: String,
        policy: Policy,
        editTimeMilliseconds: Long = System.currentTimeMillis()
    ) : Long {
        val existingPolicyId = getPolicyRow(name)?.id

        return if (existingPolicyId == null) {
            insertPolicy(authorityId, name, policy, editTimeMilliseconds)
        } else {
            updatePolicy(authorityId, name, policy, editTimeMilliseconds)
            existingPolicyId
        }
    }

    fun deletePolicy(
        name: String
    ) {
        transaction(database) {
            // Check if any user policies reference this policy - SQLite doesn't seem to
            // support onDelete = ReferenceOption.RESTRICT, so we do it manually.
            val policyId = Policies.select {
                Policies.name eq name
            }.single()[Policies.id].value

            val useCount = UserPolicies.select {
                UserPolicies.policyId eq policyId
            }.count()

            if(useCount > 0) {
                throw Exception("Cannot delete policy $name because it is referenced by $useCount user policies")
            }

            Policies.deleteWhere {
                Policies.id eq policyId
            }
        }
    }

    fun insertUserPolicy(
        authorityId: Id,
        userId: Id,
        policyId: Long,
        editTimeMilliseconds: Long = System.currentTimeMillis()
    ) : Long {
        return transaction(database) {
            UserPolicies.insert {
                it[UserPolicies.authorityId] = authorityId.encoded()
                it[UserPolicies.userId] = userId.encoded()
                it[UserPolicies.policyId] = policyId
                it[UserPolicies.editTimeMilliseconds] = editTimeMilliseconds
            }[UserPolicies.id].value
        }
    }

    fun getUserPolicyRow(
        userId: Id
    ): UserPolicyRow? {
        return getUserPolicyJoinByUserId(userId)?.let { UserPolicyRow(it) }
    }

    fun updateUserPolicy(
        authorityId: Id,
        userId: Id,
        policyId: Long,
        editTimeMilliseconds: Long = System.currentTimeMillis()
    ) {
        transaction(database) {
            UserPolicies.update({ UserPolicies.userId eq userId.encoded() }) {
                it[UserPolicies.authorityId] = authorityId.encoded()
                it[UserPolicies.userId] = userId.encoded()
                it[UserPolicies.policyId] = policyId
                it[UserPolicies.editTimeMilliseconds] = editTimeMilliseconds
            }
        }
    }

    fun upsertUserPolicy(
        authorityId: Id,
        userId: Id,
        policyId: Long,
        editTimeMilliseconds: Long = System.currentTimeMillis()
    ) {
        val existingUserPolicy = transaction(database) {
            UserPolicies.select {
                UserPolicies.userId eq userId.encoded()
            }.singleOrNull()
        }

        if (existingUserPolicy == null) {
            insertUserPolicy(authorityId, userId, policyId, editTimeMilliseconds)
        } else {
            updateUserPolicy(authorityId, userId, policyId, editTimeMilliseconds)
        }
    }

    fun deleteUserPolicy(
        userId: Id
    ) {
        transaction(database) {
            UserPolicies.deleteWhere {
                UserPolicies.userId eq userId.encoded()
            }
        }
    }
}