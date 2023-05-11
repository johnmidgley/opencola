package opencola.server.handlers

import io.opencola.application.TestApplication
import io.opencola.model.ResourceEntity
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.addPersona
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FeedTest {
    @Test
    fun testFeedWithNoResults(){
        val app = TestApplication.instance
        handleGetFeed(setOf(app.getPersonas().single().personaId), app.inject(), app.inject(), app.inject(), "")
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
        handleGetFeed(setOf(persona0.personaId), app.inject(), app.inject(), app.inject(), ""). let { result ->
            assertEquals(2, result.results.size)
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource0.entityId.toString() })
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource1.entityId.toString() })
        }

        // Check that Persona 0's feed contains only the expected resource
        handleGetFeed(setOf(persona1.personaId), app.inject(), app.inject(), app.inject(), "").let { result ->
            assertEquals(1, result.results.size)
            assertNotNull(result.results.singleOrNull { it.entityId == person1Resource0.entityId.toString() })
        }

        // Check that "All" feed contains all results
        handleGetFeed(setOf(persona0.personaId, persona1.personaId), app.inject(), app.inject(), app.inject(), "").let { result ->
            assertEquals(3, result.results.size)
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource0.entityId.toString() })
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource1.entityId.toString() })
            assertNotNull(result.results.singleOrNull { it.entityId == person1Resource0.entityId.toString() })
        }

        // Like one of Persona 0's resources from Persona 1 from "all" context
        val bothPersonasContext = Context(persona0.personaId, persona1.entityId)
        val likeP0r0Payload = EntityPayload(person0Resource0.entityId.toString(), like = true)
        updateEntity(app.inject(), app.inject(),bothPersonasContext, persona1, likeP0r0Payload)!!.let { result ->
            val activities = result.activities.filter { it.authorityId == persona1.personaId.toString() }
            assertEquals(2, activities.size)
            assertNotNull(activities.single { it.actions.singleOrNull { it.type == "save" } != null })
            assertNotNull(activities.single { it.actions.singleOrNull { it.type == "like" }?.value == "true" })

            // Result should also contain Persona 0's activity
            assertNotNull(result.activities.firstOrNull { it.authorityId == persona0.personaId.toString() })
        }

        // Persona1 should now have 2 results
        handleGetFeed(setOf(persona1.personaId), app.inject(), app.inject(), app.inject(), "").let { result ->
            assertEquals(2, result.results.size)
            assertNotNull(result.results.singleOrNull { it.entityId == person1Resource0.entityId.toString() })
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource0.entityId.toString() })
        }

        // Add a comment from Persona 1 to Persona 0's 2nd resource
        val postCommentPayload = PostCommentPayload(null, "Comment from persona 1")
        updateComment(app.inject(), app.inject(), bothPersonasContext, persona1, person0Resource1.entityId, postCommentPayload)!!.let { result ->
            val activities = result.activities.filter { it.authorityId == persona1.personaId.toString() }
            assertEquals(2, activities.size)
            assertNotNull(activities.single { it.actions.singleOrNull { it.type == "save" } != null })
            assertNotNull(activities.single { it.actions.singleOrNull { it.type == "comment" }?.value == "Comment from persona 1" })
        }

        // Persona1 should now have 3 results
        handleGetFeed(setOf(persona1.personaId), app.inject(), app.inject(), app.inject(), "").let { result ->
            assertEquals(3, result.results.size)
            assertNotNull(result.results.singleOrNull { it.entityId == person1Resource0.entityId.toString() })
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource0.entityId.toString() })
            assertNotNull(result.results.singleOrNull { it.entityId == person0Resource1.entityId.toString() })
        }

        // Check that Persona 0's feed only contains activity for Persona 0
        assert(!handleGetFeed(setOf(persona0.personaId), app.inject(), app.inject(), app.inject(), "").results.flatMap {
            it.activities.filter { activity -> activity.authorityId != persona0.personaId.toString() }
        }.any())

        // Check that Persona 1's feed only contains activity for Persona 1
        assert(!handleGetFeed(setOf(persona1.personaId), app.inject(), app.inject(), app.inject(), "").results.flatMap {
            it.activities.filter { activity -> activity.authorityId != persona1.personaId.toString() }
        }.any())
    }
}