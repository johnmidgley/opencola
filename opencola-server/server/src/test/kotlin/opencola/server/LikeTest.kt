package opencola.server

import io.ktor.client.call.*
import io.ktor.server.testing.*
import io.opencola.application.TestApplication
import io.opencola.model.PostEntity
import io.opencola.storage.addPersona
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.entitystore.EntityStore
import opencola.server.handlers.Context
import opencola.server.handlers.EntityResult
import opencola.server.handlers.LikePayload
import kotlin.test.Test
import kotlin.test.assertEquals

class LikeTest : ApplicationTestBase() {
    @Test
    fun testLikePost() = testApplication {
        application { configure(this) }
        val client = JsonClient(this)

        val app = TestApplication.instance
        val addressBook = app.inject<AddressBook>()
        val entityStore = app.inject<EntityStore>()

        // Create a post from one persona
        val persona0 = addressBook.addPersona("testLikePost 0")
        val person0Resource0 = PostEntity(persona0.personaId, "testLikePost")
        entityStore.updateEntities(person0Resource0)

        // Like the post from another persona
        val persona1 = addressBook.addPersona("testLikePost 1")
        val likePayload = LikePayload(person0Resource0.entityId, true)
        val context = Context(persona0.personaId, persona1.personaId)
        val path = "/entity/${person0Resource0.entityId}/like?personaId=${persona1.personaId}&context=$context"
        val entityResult: EntityResult = client.post(path, likePayload).body()

        // Check that the post is liked
        assertEquals(2, entityResult.activities.count())
        val likeAction = entityResult.activities.single { it.actions.single().type == "like" }.actions.single()
        assertEquals("true", likeAction.value)
        val saveAction = entityResult.activities.single { it.actions.single().type == "save" }.actions.single()
        assertEquals(null, saveAction.value)
    }
}