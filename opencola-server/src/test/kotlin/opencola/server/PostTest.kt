package opencola.server

import opencola.core.TestApplication
import opencola.core.model.Authority
import opencola.core.network.PeerRouter
import opencola.core.storage.EntityStore
import opencola.server.handlers.EntityPayload
import opencola.server.handlers.newPost
import opencola.service.EntityResult
import org.junit.Test
import org.kodein.di.instance
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PostTest {
    val app = TestApplication.instance

    @Test
    fun testNewPost() {
        val authority by app.injector.instance<Authority>()
        val entityStore by app.injector.instance<EntityStore>()
        val peerRouter by app.injector.instance<PeerRouter>()
        val entityPayload = EntityPayload("", "Name", "https://image.com", "description", true, "tag", "comment")
        val result = newPost(authority, entityStore, peerRouter, entityPayload)

        assertNotNull(result)

        val summary = result.summary
        assertEquals(summary.name, entityPayload.name)
        assertEquals(summary.imageUri, entityPayload.imageUri)
        assertEquals(summary.description, entityPayload.description)
        assertEquals(summary.uri, null)
        val activities = result.activities
        assertEquals(1, activities.size)

        val activity = activities[0]
        assertEquals(authority.authorityId.toString(), activity.authorityId)

        val actions = activity.actions
        assertEquals(4, actions.size)
        assertContains(actions, EntityResult.Action(EntityResult.ActionType.Save, null, null))
        assertContains(actions, EntityResult.Action(EntityResult.ActionType.Like, null, entityPayload.like))
        assertContains(actions, EntityResult.Action(EntityResult.ActionType.Tag, null, entityPayload.tags))
        assertEquals(actions.single() { it.type == "comment" }.value, entityPayload.comment)
    }
}