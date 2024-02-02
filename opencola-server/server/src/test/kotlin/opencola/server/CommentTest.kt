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
import opencola.server.handlers.PostCommentPayload
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentTest : ApplicationTestBase() {
    @Test
    fun testCommentOnPost() = testApplication {
        application { configure(this) }
        val client = JsonClient(this)

        val app = TestApplication.instance
        val addressBook = app.inject<AddressBook>()
        val entityStore = app.inject<EntityStore>()
        val persona0 = addressBook.addPersona("testCommentOnPost 0")
        val persona1 = addressBook.addPersona("testCommentOnPost 1")

        try {
            // Create a post from one persona
            val person0Resource0 = PostEntity(persona0.personaId, "testCommentOnPost")
            entityStore.updateEntities(person0Resource0)

            // Like the post from another persona
            val commentPayload = PostCommentPayload(null, "comment1 testCommentOnPost")
            val context = Context(persona0.personaId, persona1.personaId)
            val path = "/entity/${person0Resource0.entityId}/comment?personaId=${persona1.personaId}&context=$context"
            val entityResult: EntityResult = client.post(path, commentPayload).body()

            assertEquals(2, entityResult.activities.count())
            val saveAction = entityResult.activities.single { it.actions.first().actionType == ActionType.bubble }.actions.single()
            assertEquals(null, saveAction.value)

            // Check that the post is liked
            val commentActions = entityResult.activities.single { it.actions.first().actionType == ActionType.comment }.actions
            assertEquals(1, commentActions.count())
            assertEquals("comment1 testCommentOnPost", commentActions.single().value)
        } finally {
            addressBook.deletePersona(persona0.personaId)
            addressBook.deletePersona(persona1.personaId)
        }
    }
}