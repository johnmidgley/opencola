package opencola.server

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.opencola.model.*
import io.opencola.storage.EntityStore
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import opencola.core.TestApplication
import opencola.server.handlers.*
import org.kodein.di.instance
import java.io.File
import java.net.URI
import java.net.URLEncoder.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest : ApplicationTestBase() {
    @Test
    fun testRoot() = testApplication {
        application { configure(this) }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testGetEntity() = testApplication {
        application { configure(this) }
        val authority = application.getPersonas().first()
        val entityStore by injector.instance<EntityStore>()
        val entity =
            ResourceEntity(authority.authorityId, URI("http://opencola.org"), trust = 1.0F, like = true, rating = 1.0F)
        entityStore.updateEntities(entity)

        val response = client.get("/entity/${entity.entityId}")
        assertEquals(HttpStatusCode.OK, response.status)
        val content = response.bodyAsText()
        val entityResult = Json.decodeFromString<EntityResult>(content)
        // TODO: Can't use .equals, since returned entity has committed transaction ids.
        // Make commit return the updated entity or implement a contentEquals that ignores transaction id
        assertEquals(entity.entityId, Id.decode(entityResult.entityId))
        assertEquals(entity.uri, URI(entityResult.summary.uri!!))

        val activity = entityResult.activities.single()
        assertEquals(authority.authorityId.toString(), activity.authorityId)

        val actions = activity.actions
        assertEquals(4, actions.size)

        val trustAction = actions.single { it.type == "trust" }
        assertEquals(entity.trust.toString(), trustAction.value)

        val likeAction = actions.single { it.type == "like" }
        assertEquals(entity.like.toString(), likeAction.value)

        val ratingAction = actions.single { it.type == "rate" }
        assertEquals(entity.rating.toString(), ratingAction.value)
    }

    @Test
    fun testStatusActions() = testApplication {
        application { configure(this) }
        val authority = application.getPersonas().first()
        val entityStore by injector.instance<EntityStore>()
        val uri = URI("https://opencola.org")
        val entity = ResourceEntity(authority.authorityId, uri, trust = 1.0F, like = true, rating = 1.0F)

        entityStore.updateEntities(entity)

        val urlString = buildString {
            append("/actions/")
            append(encode(uri.toString(), "utf-8"))
        }

        val response = client.get(urlString)
        val content = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(content)
        val actions = Json.decodeFromString<Actions>(content)

        assertEquals(entity.trust, actions.trust)
        assertEquals(entity.like, actions.like)
        assertEquals(entity.rating, actions.rating)
    }

    @Test
    // TODO: Break this up!
    // TODO: Add tests for Like and trust that use this code
    fun testSavePageThenSearch() = testApplication {
        application { configure(this) }
        val mhtPath = TestApplication.applicationPath.resolve("../../sample-docs/Conway's Game of Life - Wikipedia.mht")
        val fileBytes = File(mhtPath.toString()).readBytes()

        // TODO: This should work, according to https://ktor.io/docs/testing.html#make-request
//        val client = createClient {
//            install(ContentNegotiation) {
//                json()
//            }
//        }

        val boundary = "WebAppBoundary"
        val response = client.post("/action") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("action", "save")
                        append("value", "true")
                        append("mhtml", fileBytes, Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=blob")
                        })
                    },
                    boundary,
                    ContentType.MultiPart.FormData.withParameter("boundary", boundary)
                )
            )
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        delay(500)

        val searchResponse = client.get("/search?q=game")
        assertEquals(HttpStatusCode.OK, searchResponse.status)
        val searchResults = Json.decodeFromString<SearchResults>(searchResponse.bodyAsText())
        assertEquals("Conway's Game of Life - Wikipedia", searchResults.matches.first().name)
    }

    private fun getSingleActivity(feedResult: FeedResult, type: String): EntityResult.Activity {
        return feedResult.results[0].activities.single { it.actions[0].type == type }
    }

    private fun getSingleActivityActionValue(feedResult: FeedResult, type: String): String? {
        return getSingleActivity(feedResult, type).actions[0].value
    }

    @Test
    fun testGetFeed() = testApplication {
        application { configure(this) }

        val authority = application.getPersonas().first()
        val entityStore by injector.instance<EntityStore>()
        entityStore.resetStore()

        val uri = URI("https://opencola.org/${Id.new()}")
        val entity = ResourceEntity(authority.authorityId, uri)
        entity.dataId = entity.dataId.plus(Id.new())
        entityStore.updateEntities(entity)

        entity.trust = 1.0F
        entityStore.updateEntities(entity)

        entity.like = true
        entityStore.updateEntities(entity)

        entity.rating = 0.5F
        entityStore.updateEntities(entity)

        val comment = CommentEntity(authority.authorityId, entity.entityId, "Test Comment")
        entityStore.updateEntities(comment)

        // TODO: Add another authority

        val response = client.get("/feed")
        assertEquals(HttpStatusCode.OK, response.status)
        val content = response.bodyAsText()
        val feedResult = Json.decodeFromString<FeedResult>(content)

        assertEquals(entity.entityId.toString(), feedResult.results[0].entityId)
        assertEquals(5, feedResult.results[0].activities.count())
        assertEquals(uri.toString(), feedResult.results[0].summary.uri)
        feedResult.results[0].activities.single { it.actions[0].type == "comment" }.actions[0].value
        assertEquals(entity.dataId.first().toString(), getSingleActivity(feedResult, "save").actions[0].id)
        assertEquals(entity.trust.toString(), getSingleActivityActionValue(feedResult, "trust"))
        assertEquals(entity.like.toString(), getSingleActivityActionValue(feedResult, "like"))
        assertEquals(entity.rating.toString(), getSingleActivityActionValue(feedResult, "rate"))
        assertEquals(comment.text, getSingleActivityActionValue(feedResult, "comment"))
    }

    @Test
    fun testUpdateEntity() = testApplication {
        application { configure(this) }
        val authority = application.getPersonas().first()
        val entityStore by injector.instance<EntityStore>()
        val resourceEntity = ResourceEntity(
            authority.authorityId,
            URI("https://opencola.io"),
            "Name",
            "Description",
            "Text",
            URI("https://opencola.io/image.png")
        )
        entityStore.updateEntities(resourceEntity)
        val entity = EntityPayload(
            resourceEntity.entityId.toString(),
            "Name1",
            "https://opencola.io/image1.png",
            "Description1",
            true,
            "tag",
            null
        )

        val response = client.put("/entity/${resourceEntity.entityId}") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(Json.encodeToString(entity))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val entityResult = Json.decodeFromString<EntityResult>(response.bodyAsText())

        assertEquals(entity.entityId, entityResult.entityId)
        assertEquals(entity.name, entityResult.summary.name)
        assertEquals(entity.description, entityResult.summary.description)
        assertEquals(entity.imageUri, entityResult.summary.imageUri)
    }
}