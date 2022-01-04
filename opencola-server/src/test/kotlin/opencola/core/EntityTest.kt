import opencola.core.storage.EntityStore
import opencola.core.security.generateKeyPair
import opencola.core.model.Id
import opencola.core.model.ActorEntity
import opencola.core.model.CoreAttribute
import opencola.core.model.ResourceEntity
import java.net.URI
import kotlin.test.Test
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
        assertEquals(rating, entity.rating
        )
    }

    @Test
    fun testSettingSameValue(){
        val uri = URI("opencola://test-resource")
        val entity = ResourceEntity(Id.ofUri(uri), uri)
        entity.uri = uri

        assertEquals(1, entity.getFacts().filter{ it.attribute == CoreAttribute.Uri.spec}.size)
        assertFails { entity.uri = URI("opencola://different-uri") }

        val authority = getAuthority()
        // val store = EntityStore(authority)
        //TODO: Fix entity store so adding an entity returns a new Entity, and commit occurs at the same time
        // then test here that setting a new value results in a new fact
    }

}