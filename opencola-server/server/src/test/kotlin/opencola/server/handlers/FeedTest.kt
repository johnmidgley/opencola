package opencola.server.handlers

import io.opencola.application.TestApplication
import io.opencola.model.PostEntity
import io.opencola.model.RawEntity
import io.opencola.model.ResourceEntity
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.addPersona
import io.opencola.storage.deletePersona
import opencola.server.handlers.EntityResult.ActionType
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FeedTest {
    @Test
    fun testFeedWithNoResults() {
        val app = TestApplication.instance
        val persona = app.inject<AddressBook>().addPersona("Empty Persona")
        val results = app.handleGetFeed(setOf(persona.entityId))
        assertEquals(0, results.results.size)
    }

    @Test
    fun testFeedWithPersonas() {
        val app = TestApplication.instance
        val addressBook = app.inject<AddressBook>()
        val entityStore = app.inject<EntityStore>()

        // Create persona 0 with 2 resources
        val persona0 = addressBook.addPersona("Persona 0")
        val person0Resource0 = ResourceEntity(persona0.personaId, URI("https://uri0"))
        val person0Resource1 = ResourceEntity(persona0.personaId, URI("https://uri1"))
        entityStore.updateEntities(person0Resource0, person0Resource1)

        // Create person 1 with 1 resource
        val persona1 = addressBook.addPersona("Persona 1")
        val person1Resource0 = ResourceEntity(persona1.personaId, URI("https://uri3"))
        entityStore.updateEntities(person1Resource0)

        // Check that Persona 0's feed contains both and only the expected resources
        app.handleGetFeed(setOf(persona0.personaId)).let { result ->
            assertEquals(2, result.results.size)
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource0.entityId.toString() })
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource1.entityId.toString() })
        }

        // Check that Persona 0's feed contains only the expected resource
        app.handleGetFeed(setOf(persona1.personaId)).let { result ->
            assertEquals(1, result.results.size)
            assertNotNull(result.results.singleOrNull { it.entityId == person1Resource0.entityId.toString() })
        }

        // Check that "All" feed contains all results
        app.handleGetFeed(setOf(persona0.personaId, persona1.personaId)).let { result ->
            assertEquals(3, result.results.size)
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource0.entityId.toString() })
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource1.entityId.toString() })
            assertNotNull(result.results.singleOrNull { it.entityId == person1Resource0.entityId.toString() })
        }

        // Like one of Persona 0's resources from Persona 1 from "all" context
        val bothPersonasContext = Context(persona0.personaId, persona1.entityId)
        val likePayload = EntityPayload(person0Resource0.entityId.toString(), like = true)
        updateEntity(
            app.inject(),
            app.inject(),
            app.inject(),
            app.inject(),
            bothPersonasContext,
            persona1,
            likePayload
        )!!.let { result ->
            val activities = result.activities.filter { it.authorityId == persona1.personaId.toString() }
            assertEquals(2, activities.size)
            assertNotNull(activities.single { it.actions.singleOrNull { it.actionType == ActionType.bubble } != null })
            assertNotNull(activities.single { it.actions.singleOrNull { it.actionType == ActionType.like }?.value == "true" })

            // Result should also contain Persona 0's activity
            assertNotNull(result.activities.firstOrNull { it.authorityId == persona0.personaId.toString() })
        }

        // Persona1 should now have 2 results
        app.handleGetFeed(setOf(persona1.personaId)).let { result ->
            assertEquals(2, result.results.size)
            assertNotNull(result.results.singleOrNull { it.entityId == person1Resource0.entityId.toString() })
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource0.entityId.toString() })
        }

        // Add a comment from Persona 1 to Persona 0's 2nd resource
        val postCommentPayload = PostCommentPayload(null, "Comment from persona 1")
        updateComment(
            app.inject(),
            app.inject(),
            app.inject(),
            app.inject(),
            bothPersonasContext,
            persona1,
            person0Resource1.entityId,
            postCommentPayload
        )!!.let { result ->
            val activities = result.activities.filter { it.authorityId == persona1.personaId.toString() }
            assertEquals(1, activities.size)
            assertNotNull(activities.single { it.actions.singleOrNull { it.actionType == ActionType.comment }?.value == "Comment from persona 1" })
        }

        // Persona1 should now have 2 results
        app.handleGetFeed(setOf(persona1.personaId)).let { result ->
            assertEquals(2, result.results.size)
            assertNotNull(result.results.singleOrNull { it.entityId == person1Resource0.entityId.toString() })
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource0.entityId.toString() })
        }

        // Check that Persona 0's feed only contains activity for Persona 0
        assert(!app.handleGetFeed(setOf(persona0.personaId)).results.flatMap {
            it.activities.filter { activity -> activity.authorityId != persona0.personaId.toString() }
        }.any())

        // Check that Persona 1's feed only contains activity for Persona 1
        assert(!app.handleGetFeed(setOf(persona1.personaId)).results.flatMap {
            it.activities.filter { activity -> activity.authorityId != persona1.personaId.toString() }
        }.any())
    }

    @Test
    fun testFeedWithRawEntity() {
        val app = TestApplication.instance
        val addressBook = app.inject<AddressBook>()
        val entityStore = app.inject<EntityStore>()

        val persona0 = addressBook.addPersona("Persona 0")
        val person0Resource0 = ResourceEntity(persona0.personaId, URI("https://uri0"))
        entityStore.updateEntities(person0Resource0)

        // Added a comment from person1 onto person0's resource
        val persona1 = addressBook.addPersona("Persona 1")
        val persona1RawEntity = RawEntity(persona1.personaId, person0Resource0.entityId)
        persona1RawEntity.like = true
        entityStore.updateEntities(persona1RawEntity)

        app.handleGetFeed(setOf(persona0.personaId, persona1.personaId)).let { result ->
            assertEquals(1, result.results.size)
            val entityResult = result.results.singleOrNull { it.entityId == person0Resource0.entityId.toString() }
            assertNotNull(entityResult)
            entityResult.activities.filter { it.authorityId == persona1.personaId.toString() }.let { activities ->
                assertEquals(1, activities.size)
                assertNotNull(activities.singleOrNull { activity -> activity.actions.singleOrNull { it.actionType == ActionType.like } != null })
            }
        }

        app.handleGetFeed(setOf(persona0.personaId)).let { result ->
            assertEquals(1, result.results.size)
            val entityResult = result.results.singleOrNull { it.entityId == person0Resource0.entityId.toString() }
            assertNotNull(entityResult)
        }

        // Make sure that no results come back for persona1, since no items have been fully saved
        assertEquals(0, app.handleGetFeed(setOf(persona1.personaId)).results.size)
    }

    @Test
    fun testCommentReply() {
        val app = TestApplication.instance
        val addressBook = app.inject<AddressBook>()
        val entityStore = app.inject<EntityStore>()

        val persona0 = addressBook.addPersona("testCommentChain0")
        val resource = ResourceEntity(persona0.personaId, URI("https://uri0"))
        entityStore.updateEntities(resource)

        // Added a comment from person1 onto person0's resource
        val comment = app.updateComment(persona0, resource.entityId, null, "top level comment")

        val persona1 = addressBook.addPersona("testCommentChain0")
        app.updateComment(persona1, comment.entityId, null, "comment reply")

        app.handleGetFeed(setOf(persona0.personaId, persona1.personaId)).let { result ->
            assertEquals(1, result.results.size)
            val entityResult = result.results.singleOrNull { it.entityId == resource.entityId.toString() }
            assertNotNull(entityResult)
            val comments = entityResult.activities.flatMap { it.actions.filter { it.actionType == ActionType.comment } }
            assertEquals(2, comments.size)

            val comment0 = comments.single { it.value == "top level comment" }
            // Only comment replies should have a parent id.
            // When not present, the top level entity is the implied parent
            assertNull(comment0.parentId)
            val comment1 = comments.single { it.value == "comment reply" }
            assertEquals(comment.entityId.toString(), comment1.parentId)
        }

        entityStore.deleteEntities(comment.authorityId, comment.entityId)

        // Deleting comment should stop the reply from being returned, so we expect 0 comments
        app.handleGetFeed(setOf(persona0.personaId, persona1.entityId)).let { result ->
            assertEquals(1, result.results.size)
            val entityResult = result.results.singleOrNull { it.entityId == resource.entityId.toString() }
            assertNotNull(entityResult)
            val comments = entityResult.activities.flatMap { it.actions.filter { it.actionType == ActionType.comment } }
            assertEquals(0, comments.size)
        }
    }

    @Test
    fun testFeedOrdering() {
        // TODO: Think about making a FeedContext that can be used without the test application
        val app = TestApplication.instance
        val addressBook = app.inject<AddressBook>()
        val entityStore = app.inject<EntityStore>()

        val persona = addressBook.addPersona("testFeedOrdering")

        try {
            val post0 = PostEntity(persona.personaId, "post0").also { entityStore.updateEntities(it) }
            val post1 = PostEntity(persona.personaId, "post1", like = true).also { entityStore.updateEntities(it) }
            val post2 = PostEntity(persona.personaId, "post2").also { entityStore.updateEntities(it) }

            // Test that feed is ordered reverse chronologically
            app.handleGetFeed(setOf(persona.personaId)).let { result ->
                assertEquals(3, result.results.size)
                assertEquals(post2.entityId.toString(), result.results[0].entityId)
                assertEquals(post1.entityId.toString(), result.results[1].entityId)
                assertEquals(post0.entityId.toString(), result.results[2].entityId)
            }

            post0.like = true
            entityStore.updateEntities(post0)

            // Test the liking an item bumps it to the top
            app.handleGetFeed(setOf(persona.personaId)).let { result ->
                assertEquals(3, result.results.size)
                assertEquals(post0.entityId.toString(), result.results[0].entityId)
                assertEquals(post2.entityId.toString(), result.results[1].entityId)
                assertEquals(post1.entityId.toString(), result.results[2].entityId)
            }

            // Test that unliking an item does not move it
            post1.like = null
            entityStore.updateEntities(post1)

            app.handleGetFeed(setOf(persona.personaId)).let { result ->
                assertEquals(3, result.results.size)
                assertEquals(post0.entityId.toString(), result.results[0].entityId)
                assertEquals(post2.entityId.toString(), result.results[1].entityId)
                assertEquals(post1.entityId.toString(), result.results[2].entityId)
            }
        } finally {
            addressBook.deletePersona(persona.personaId)
        }
    }

    @Test
    fun testFeedPaging() {
        val app = TestApplication.instance
        val addressBook = app.inject<AddressBook>()
        val entityStore = app.inject<EntityStore>()
        val persona = addressBook.addPersona("testFeedPaging")

        val postEntities = (0..8).map { i ->
            PostEntity(persona.personaId, "post$i").also { entityStore.updateEntities(it) }
        }

        val results1 = app.handleGetFeed(setOf(persona.entityId), 2, null)
        assertEquals(2, results1.results.size)
        val results2 = app.handleGetFeed(setOf(persona.entityId), 2, results1.pagingToken)
        assertEquals(2, results2.results.size)
        val results3 = app.handleGetFeed(setOf(persona.entityId), 2, results2.pagingToken)
        assertEquals(2, results3.results.size)
        val results4 = app.handleGetFeed(setOf(persona.entityId), 2, results3.pagingToken)
        assertEquals(2, results4.results.size)
        val results5 = app.handleGetFeed(setOf(persona.entityId), 2, results4.pagingToken)
        assertEquals(1, results5.results.size)
        assertNull(results5.pagingToken)

        val postEntityIds = postEntities.map { it.entityId.toString() }.toSet()
        val resultEntityIds = results1.results
            .asSequence()
            .map { it.entityId }
            .plus(results2.results.map { it.entityId })
            .plus(results3.results.map { it.entityId })
            .plus(results4.results.map { it.entityId })
            .plus(results5.results.map { it.entityId })
            .toSet()

        assertEquals(postEntityIds, resultEntityIds)
    }
}