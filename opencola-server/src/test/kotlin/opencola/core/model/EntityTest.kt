package opencola.core.model

import opencola.core.TestApplication
import opencola.core.security.generateKeyPair
import org.kodein.di.instance
import java.net.URI
import kotlin.test.*

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
        val authorityId = Id.ofData("".toByteArray())
        val comment1 = CommentEntity(authorityId, "comment1", true, .5f)
        val comment2 = CommentEntity(authorityId, "comment2", false, .3f)
        val entity = ResourceEntity(authorityId, URI("https://test.com"))

        comment1.commitFacts(2,2)
        assertEquals(comment1.authorityId, authorityId)
        assertEquals(comment1.text, "comment1")
        assertEquals(comment1.like, true)
        assertEquals(comment1.rating, .5f)

        entity.commentIds += comment1.entityId
        entity.commentIds += comment2.entityId
        entity.commitFacts(0,0)
        assertEquals(2, entity.commentIds.count())
        assertContains(entity.commentIds, comment1.entityId)
        assertContains(entity.commentIds, comment2.entityId)

        entity.commentIds -= comment1.entityId
        entity.commitFacts(1,1)
        assertEquals(1, entity.commentIds.count())
        assertContains(entity.commentIds, comment2.entityId)
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