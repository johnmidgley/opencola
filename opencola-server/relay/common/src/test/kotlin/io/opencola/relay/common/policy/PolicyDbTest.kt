package io.opencola.relay.common.policy

import Policies
import io.opencola.model.Id
import io.opencola.storage.db.getPostgresDB
import io.opencola.storage.newSQLiteDB
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

class PolicyDbTest {
    private fun getPostgresDB(): Database {
        val db = getPostgresDB("localhost", "opencola", "opencola", "test")

        transaction(db) {
            SchemaUtils.drop(UserPolicies) // Drop UserPolicies first because it refers to Policies
            SchemaUtils.drop(Policies)
        }

        return db
    }

    fun getUserPolicyDB(name: String): UserPolicyDB {
        // return UserPolicyDB(getPostgresDB())
        return UserPolicyDB(newSQLiteDB("PolicyTest-$name"))
    }

    @Test
    fun testPoliciesCRUD() {
        val userPolicyDB = getUserPolicyDB("testPoliciesCRUD")
        val authorityId = Id.new()
        val policyName = "testPolicy"
        val policy = Policy(
            AdminPolicy(true, true),
            ConnectionPolicy(false),
            MessagePolicy(1024),
            StoragePolicy(2048)
        )
        val updateTimeMilliseconds = 10L

        println("Test insert")
        val policyId = userPolicyDB.upsertPolicy(authorityId, policyName, policy, updateTimeMilliseconds)
        val policyRow = userPolicyDB.getPolicyRow(policyName)
        assertNotNull(policyRow)
        assertEquals(policyId, policyRow.id)
        assertEquals(authorityId, policyRow.authorityId)
        assertEquals(policyName, policyRow.name)
        assertEquals(policy, policyRow.policy)
        assertEquals(updateTimeMilliseconds, policyRow.editTimeMilliseconds)

        println("Test fail duplicate insert")
        assertFails { userPolicyDB.insertPolicy(authorityId, policyName, policy, updateTimeMilliseconds) }

        val authorityId1 = Id.new()
        val policy1 = Policy(
            AdminPolicy(true, true),
            ConnectionPolicy(true),
            MessagePolicy(1024),
            StoragePolicy(2048)
        )
        val updateTimeMilliseconds1 = 20L

        println("Test update")
        userPolicyDB.upsertPolicy(authorityId1, policyName, policy1, updateTimeMilliseconds1)
        val policyRow1 = userPolicyDB.getPolicyRow(policyName)
        assertNotNull(policyRow1)
        assertEquals(policyId, policyRow1.id)
        assertEquals(authorityId1, policyRow1.authorityId)
        assertEquals(policyName, policyRow1.name)
        assertEquals(policy1, policyRow1.policy)
        assertEquals(updateTimeMilliseconds1, policyRow1.editTimeMilliseconds)

        println("Test delete")
        userPolicyDB.deletePolicy(policyName)
        assertNull(userPolicyDB.getPolicyRow(policyName))
    }

    @Test
    fun testUserPoliciesCRUD() {
        val userPolicyDB = getUserPolicyDB("testUserPolicyDbCRUD")
        val authorityId = Id.new()
        val policyName = "testPolicy"
        val policy = Policy(
            AdminPolicy(true, true),
            ConnectionPolicy(false),
            MessagePolicy(1024),
            StoragePolicy(2048)
        )
        val policyId = userPolicyDB.upsertPolicy(authorityId, policyName, policy)

        println("Test insert")
        val userId = Id.new()
        val updateTimeMilliseconds = 10L
        userPolicyDB.upsertUserPolicy(authorityId, userId, policyId, updateTimeMilliseconds)
        val userPolicyRow = userPolicyDB.getUserPolicyRow(userId)
        assertNotNull(userPolicyRow)
        assertEquals(userId, userPolicyRow.userId)
        assertEquals(policyId, userPolicyRow.policyId)
        assertEquals(updateTimeMilliseconds, userPolicyRow.editTimeMilliseconds)

        println("Test get policy by userId")
        val retrievedPolicy = userPolicyDB.getPolicyRow(userId)
        assertNotNull(retrievedPolicy)
        assertEquals(policyId, retrievedPolicy.id)
        assertEquals(authorityId, retrievedPolicy.authorityId)
        assertEquals(policyName, retrievedPolicy.name)
        assertEquals(policy, retrievedPolicy.policy)


        println("Test fail duplicate insert")
        assertFails { userPolicyDB.insertUserPolicy(authorityId, userId, policyId, updateTimeMilliseconds) }
        println("Test fail insert with invalid policyId")
        assertFails { userPolicyDB.insertUserPolicy(authorityId, userId, policyId + 1, updateTimeMilliseconds) }
        println("Test fail delete in use policy")
        assertFails { userPolicyDB.deletePolicy(policyName) }

        println("Test update")
        val authorityId1 = Id.new()
        val policy1 = Policy(
            AdminPolicy(true, true),
            ConnectionPolicy(true),
            MessagePolicy(1024),
            StoragePolicy(2048)
        )
        val policyId1 = userPolicyDB.upsertPolicy(authorityId1, policyName, policy1)
        val updateTimeMilliseconds1 = 20L

        userPolicyDB.updateUserPolicy(authorityId1, userId, policyId1, updateTimeMilliseconds1)
        val userPolicyRow1 = userPolicyDB.getUserPolicyRow(userId)
        assertNotNull(userPolicyRow1)
        assertEquals(userId, userPolicyRow1.userId)
        assertEquals(policyId1, userPolicyRow1.policyId)
        assertEquals(updateTimeMilliseconds1, userPolicyRow1.editTimeMilliseconds)

        println("Test delete")
        userPolicyDB.deleteUserPolicy(userId)
        assertNull(userPolicyDB.getUserPolicyRow(userId))

        println("Test delete policy")
        userPolicyDB.deletePolicy(policyName)
        assertNull(userPolicyDB.getPolicyRow(policyName))
    }
}