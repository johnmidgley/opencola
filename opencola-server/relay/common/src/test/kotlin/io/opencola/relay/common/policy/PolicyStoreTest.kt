package io.opencola.relay.common.policy

import io.opencola.model.Id
import io.opencola.storage.newSQLiteDB
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PolicyStoreTest {
    @Test
    fun testPolicyStore() {
        val db = newSQLiteDB("PolicyStoreTest")
        val rootId = Id.new()
        val testPolicyName = "testPolicy"
        val testPolicy = Policy(AdminPolicy(false, false))
        val defaultPolicy = Policy()
        val policyStore = ExposedPolicyStore(db, rootId, defaultPolicy)

        println("setPolicy with invalid authorityId")
        assertFails { policyStore.setPolicy(Id.new(), testPolicyName, Policy()) }

        println("setPolicy with valid authorityId")
        policyStore.setPolicy(rootId, testPolicyName, testPolicy)
        assertEquals(testPolicy, policyStore.getPolicy(testPolicyName))

        println(" setUserPolicy with valid but unauthorized authorityId")
        val unauthorizedPolicyEditorId = Id.new()
        assertFails { policyStore.setUserPolicy(unauthorizedPolicyEditorId, Id.new(), testPolicyName) }

        println("setUserPolicy with valid and authorized authorityId but bad policy name")
        val userId = Id.new()
        assertFails { policyStore.setUserPolicy(rootId, userId,"badPolicy") }

        println("setUserPolicy with valid and authorized authorityId")
        policyStore.setUserPolicy(rootId, userId, testPolicyName)
        assertEquals(testPolicy, policyStore.getUserPolicy(userId))

        println("Det Policy and UserPolicy with unauthorized by valid user")
        assertFails { policyStore.setPolicy(userId, testPolicyName, testPolicy) }
        assertFails { policyStore.setUserPolicy(userId, Id.new(), testPolicyName) }

        println("Grant user access to edit policies and user policies")
        val adminAccessPolicyName = "adminAccessPolicy"
        val adminAccessPolicy = Policy(AdminPolicy(true, true))
        policyStore.setPolicy(rootId, adminAccessPolicyName, adminAccessPolicy)
        policyStore.setUserPolicy(rootId, userId, adminAccessPolicyName)
        assertEquals(adminAccessPolicy, policyStore.getUserPolicy(userId))

        val user1Id = Id.new()
        policyStore.setPolicy(userId, testPolicyName, testPolicy)
        policyStore.setUserPolicy(userId, user1Id, testPolicyName)
        assertEquals(testPolicy, policyStore.getUserPolicy(user1Id))

        println("Test default policy")
        assertEquals(defaultPolicy, policyStore.getUserPolicy(Id.new()))
    }
}