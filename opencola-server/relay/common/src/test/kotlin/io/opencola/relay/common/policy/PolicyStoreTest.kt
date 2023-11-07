package io.opencola.relay.common.policy

import io.opencola.model.Id
import io.opencola.storage.newSQLiteDB
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PolicyStoreTest {
    private fun testPolicyStore(policyStore: PolicyStore) {
        val testPolicy = Policy("testPolicy", AdminPolicy(false, false))
        val defaultPolicy = Policy("default")

        println("setPolicy with invalid authorityId")
        assertFails { policyStore.setPolicy(Id.new(), defaultPolicy) }

        println("setPolicy with valid authorityId")
        policyStore.setPolicy(policyStore.rootId, testPolicy)
        assertEquals(testPolicy, policyStore.getPolicy(policyStore.rootId, testPolicy.name))

        println(" setUserPolicy with valid but unauthorized authorityId")
        val unauthorizedPolicyEditorId = Id.new()
        assertFails { policyStore.setUserPolicy(unauthorizedPolicyEditorId, Id.new(), testPolicy.name) }

        println("setUserPolicy with valid and authorized authorityId but bad policy name")
        val userId = Id.new()
        assertFails { policyStore.setUserPolicy(policyStore.rootId, userId,"badPolicy") }

        println("setUserPolicy with valid and authorized authorityId")
        policyStore.setUserPolicy(policyStore.rootId, userId, testPolicy.name)
        assertEquals(testPolicy, policyStore.getUserPolicy(policyStore.rootId, userId))

        println("Det Policy and UserPolicy with unauthorized by valid user")
        assertFails { policyStore.setPolicy(userId, testPolicy) }
        assertFails { policyStore.setUserPolicy(userId, Id.new(), testPolicy.name) }

        println("Grant user access to edit policies and user policies")
        val adminAccessPolicyName = "adminAccessPolicy"
        val adminAccessPolicy = Policy("adminAccessPolicy", AdminPolicy(isAdmin = true, canEditPolicies = true, canEditUserPolicies = true))
        policyStore.setPolicy(policyStore.rootId, adminAccessPolicy)
        policyStore.setUserPolicy(policyStore.rootId, userId, adminAccessPolicyName)
        assertEquals(adminAccessPolicy, policyStore.getUserPolicy(policyStore.rootId, userId))

        val user1Id = Id.new()
        policyStore.setPolicy(userId, testPolicy)
        policyStore.setUserPolicy(userId, user1Id, testPolicy.name)
        assertEquals(testPolicy, policyStore.getUserPolicy(policyStore.rootId, user1Id))

        println("Test default policy")
        assertEquals(defaultPolicy, policyStore.getUserPolicy(policyStore.rootId, Id.new()))
    }

    @Test
    fun testMemoryPolicyStore(){
        testPolicyStore(MemoryPolicyStore(Id.new(), Policy("default")))
    }

    @Test
    fun testExposedPolicyStore(){
        testPolicyStore(ExposedPolicyStore(newSQLiteDB("PolicyStoreTest"), Id.new(), Policy("default")))
    }
}