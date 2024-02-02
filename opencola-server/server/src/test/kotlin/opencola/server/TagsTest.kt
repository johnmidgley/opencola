package opencola.server

import io.ktor.client.call.*
import io.ktor.server.testing.*
import io.opencola.application.TestApplication
import io.opencola.model.PostEntity
import io.opencola.storage.addPersona
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.deletePersona
import io.opencola.storage.entitystore.EntityStore
import opencola.server.handlers.Context
import opencola.server.handlers.EntityResult
import opencola.server.handlers.EntityResult.ActionType
import opencola.server.handlers.TagsPayload
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TagsTest : ApplicationTestBase() {
    @Test
    fun testTagPost() = testApplication {
        application { configure(this) }
        val client = JsonClient(this)

        val app = TestApplication.instance
        val addressBook = app.inject<AddressBook>()
        val entityStore = app.inject<EntityStore>()
        val persona0 = addressBook.addPersona("testTagPost 0")
        val persona1 = addressBook.addPersona("testTagPost 1")

        try {
            // Create a post from one persona
            val person0Resource0 = PostEntity(persona0.personaId, "testTagPost")
            entityStore.updateEntities(person0Resource0)

            // Like the post from another persona
            val tagsPayload = TagsPayload("tag1 tag2")
            val context = Context(persona0.personaId, persona1.personaId)
            val path = "/entity/${person0Resource0.entityId}/tags?personaId=${persona1.personaId}&context=$context"
            val entityResult: EntityResult = client.post(path, tagsPayload).body()

            assertEquals(2, entityResult.activities.count())
            val saveAction = entityResult.activities.single { it.actions.first().actionType == ActionType.bubble }.actions.single()
            assertEquals(null, saveAction.value)

            // Check that the post is liked
            val tagsActions = entityResult.activities.single { it.actions.first().actionType == ActionType.tag }.actions
            assertEquals(2, tagsActions.count())
            val tags = tagsActions.map { it.value }
            assertContains(tags, "tag1")
            assertContains(tags, "tag2")
        } finally {
            addressBook.deletePersona(persona0.personaId)
            addressBook.deletePersona(persona1.personaId)
        }
    }
}