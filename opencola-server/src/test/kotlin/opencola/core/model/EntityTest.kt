package opencola.core.model

import opencola.core.TestApplication
import opencola.core.security.generateKeyPair
import org.kodein.di.instance
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class EntityTest {
    @Test
    fun testActorEntity(){
        val uri = URI("opencola://12345")
        val imageUri = URI("http://images/123456")
        val name = "name"
        val description = "description"
        val publicKey = generateKeyPair().public
        val like = true
        val trust = .75F
        val tags = listOf("this", "that").toSet()
        val rating = .37F

        val entity = ActorEntity(Id.ofPublicKey(publicKey), publicKey)
        entity.uri = uri
        assertEquals(uri, entity.uri)

        entity.imageUri = imageUri
        assertEquals(imageUri, entity.imageUri)

        entity.name = name
        assertEquals(name, entity.name)

        entity.description = description
        assertEquals(description, entity.description)

        entity.publicKey = publicKey
        assertEquals(publicKey, entity.publicKey)

        entity.like = like
        assertEquals(like, entity.like)

        entity.trust = trust
        assertEquals(trust, entity.trust)

        entity.tags = tags
        assertEquals(tags, entity.tags)

        entity.rating = rating
        assertEquals(rating, entity.rating)
    }

    @Test
    fun testComments(){
        val entity = ResourceEntity(Id.ofData("".toByteArray()), URI("https://test.com"))

        val comment1 = MultiValueString("Comment 1")
        val comment2 = MultiValueString("Comment 2")
        val comments = listOf(comment1, comment2)
        entity.comments = comments
        entity.commitFacts(0, 1)
        val comments1 = entity.comments
        assertContentEquals(comments, comments1)

        val modifiedComment2 = MultiValueString(comment2.key, "Modified comment 2")
        val modifiedComments = listOf(comment1, modifiedComment2)
        entity.comments = modifiedComments
        entity.commitFacts(1, 2)
        val comments2 = entity.comments
        assertContentEquals(modifiedComments, comments2)

        val commentsWithDeletion = modifiedComments.drop(1).toList()
        entity.comments = commentsWithDeletion
        entity.commitFacts(2, 3)
        val comments3 = entity.comments
        assertContentEquals(commentsWithDeletion, comments3)
    }

    @Test
    fun testSettingSameValue(){
        val uri = URI("opencola://test-resource")
        val entity = ResourceEntity(Id.ofUri(uri), uri)
        entity.uri = uri

        assertEquals(1, entity.getFacts().filter{ it.attribute == CoreAttribute.Uri.spec}.size)
        assertFails { entity.uri = URI("opencola://different-uri") }

        val authority = TestApplication.instance.injector.instance<Authority>()
        // val store = EntityStore(authority)
        //TODO: Fix entity store so adding an entity returns a new Entity, and commit occurs at the same time
        // then test here that setting a new value results in a new fact
    }

}