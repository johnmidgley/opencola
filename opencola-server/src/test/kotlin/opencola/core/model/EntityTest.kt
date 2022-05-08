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
        val entity = ResourceEntity(authorityId, URI("https://test.com"))
        val comment1 = CommentEntity(authorityId, entity.entityId,"comment1", true, .5f)

        entity.commitFacts(0,0)
        val comment1Facts = comment1.commitFacts(1,1)
        assertEquals(comment1.authorityId, authorityId)
        assertEquals(comment1.text, "comment1")
        assertEquals(comment1.like, true)
        assertEquals(comment1.rating, .5f)

        val computedFact1 = computeEntityCommentIds(comment1Facts).single()
        assertEquals(computedFact1.authorityId, authorityId)
        assertEquals(computedFact1.entityId, entity.entityId)
        assertEquals(computedFact1.attribute, CoreAttribute.CommentIds.spec)
        assertEquals(computedFact1.value, Value(Id.encode(comment1.entityId)))
        assertEquals(computedFact1.operation, Operation.Add)
    }

    @Test
    fun testSettingSameValue(){
        val uri = URI("opencola://test-resource")
        val entity = ResourceEntity(Id.ofUri(uri), uri)
        entity.uri = uri

        assertEquals(1, entity.getAllFacts().filter{ it.attribute == CoreAttribute.Uri.spec}.size)
        assertFails { entity.uri = URI("opencola://different-uri") }

        val authority = TestApplication.instance.injector.instance<Authority>()
        // val store = EntityStore(authority)
        //TODO: Fix entity store so adding an entity returns a new Entity, and commit occurs at the same time
        // then test here that setting a new value results in a new fact
    }

}