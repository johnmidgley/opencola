package opencola.server

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.testing.*
import io.ktor.utils.io.streams.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import opencola.core.TestApplication
import opencola.core.model.*
import opencola.core.network.PeerRouter
import opencola.core.security.generateKeyPair
import opencola.core.storage.AddressBook
import opencola.core.storage.EntityStore
import opencola.server.handlers.EntityPayload
import opencola.server.handlers.FeedResult
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureRouting
import opencola.service.EntityResult
import opencola.service.search.SearchResults
import org.kodein.di.instance
import java.io.File
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest {
    private val application = TestApplication.instance
    val injector = TestApplication.instance.injector

    @Test
    fun testRoot() {
        withTestApplication({ configureRouting(application) }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun testGetEntity(){
        val authority by injector.instance<Authority>()
        val entityStore by injector.instance<EntityStore>()
        val entity = ResourceEntity(authority.authorityId, URI("http://opencola.org"), trust = 1.0F, like = true, rating = 1.0F)

        entityStore.updateEntities(entity)

        withTestApplication({ configureRouting(application); configureContentNegotiation() }) {
            handleRequest(HttpMethod.Get, "/entity/${entity.entityId}").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.content)
                val entityResult = Json.decodeFromString<EntityResult>(response.content!!)
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
        }
    }

    @Test
    fun testStatusActions(){
        val authority by injector.instance<Authority>()
        val entityStore by injector.instance<EntityStore>()
        val uri = URI("https://opencola.org")
        val entity = ResourceEntity(authority.authorityId, uri, trust = 1.0F, like = true, rating = 1.0F)

        entityStore.updateEntities(entity)

        withTestApplication({ configureRouting(application); configureContentNegotiation() }) {
            handleRequest(HttpMethod.Get, "/actions/${URLEncoder.encode(uri.toString(), "utf-8")}").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.content)
                val actions = Json.decodeFromString<Actions>(response.content!!)

                assertEquals(entity.trust, actions.trust)
                assertEquals(entity.like, actions.like)
                assertEquals(entity.rating, actions.rating)
            }
        }
    }



    @Test
    // TODO: Break this up!
    // TODO: Add tests for Like and trust that use this code
    fun testSavePageThenSearch(){
        val mhtPath = TestApplication.applicationPath.resolve("../sample-docs/Conway's Game of Life - Wikipedia.mht")

        withTestApplication({ configureRouting(application); configureContentNegotiation() }) {
            with(handleRequest(HttpMethod.Post, "/action"){
                val boundary = "WebAppBoundary"
                val fileBytes = File(mhtPath.toString()).readBytes()

                addHeader(HttpHeaders.ContentType, ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString())
                setBody(boundary, listOf(
                    PartData.FormItem("save", { }, headersOf(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Inline
                            .withParameter(ContentDisposition.Parameters.Name, "action")
                            .toString()
                    )),
                    PartData.FormItem("true", { }, headersOf(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Inline
                            .withParameter(ContentDisposition.Parameters.Name, "value")
                            .toString()
                    )),
                    PartData.FileItem({fileBytes.inputStream().asInput()}, {}, headersOf(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.File
                            .withParameter(ContentDisposition.Parameters.Name, "mhtml")
                            .withParameter(ContentDisposition.Parameters.FileName, "blob")
                            .toString(),
                    ))
                ))
            }) {
                assertEquals(HttpStatusCode.Accepted, response.status())
            }

            Thread.sleep(500)

            handleRequest(HttpMethod.Get, "/search?q=game").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val searchResults = Json.decodeFromString<SearchResults>(response.content!!)
                assertEquals("Conway's Game of Life - Wikipedia", searchResults.matches.first().name)
            }
        }
    }

    @Test
    fun testPostNotification(){
        // TODO: This seems to spit a few errors - should be fixed with PeerRouter updates

        val localAuthority by injector.instance<Authority>()
        val addressBook by injector.instance<AddressBook>()
        val peerAuthority = addressBook.updateAuthority(Authority(localAuthority.authorityId, generateKeyPair().public, URI(""), "Test"))
        val notification = PeerRouter.Notification(peerAuthority.authorityId, PeerRouter.Event.NewTransaction)

        withTestApplication({ configureRouting(application); configureContentNegotiation() }) {
            with(handleRequest(HttpMethod.Post, "/notifications"){
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(Json.encodeToString(notification))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    private fun getSingleActivity(feedResult: FeedResult, type: String): EntityResult.Activity {
        return feedResult.results[0].activities.single { it.actions[0].type == type }
    }

    private fun getSingleActivityActionValue(feedResult: FeedResult, type: String): String? {
        return getSingleActivity(feedResult, type).actions[0].value
    }

    @Test
    fun testGetFeed(){
        val authority by injector.instance<Authority>()
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

        withTestApplication({ configureRouting(application); configureContentNegotiation() }) {
            handleRequest(HttpMethod.Get, "/feed").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.content)
                val feedResult = Json.decodeFromString<FeedResult>(response.content!!)

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
        }
    }

    @Test
    fun testUpdateEntity(){
        val authority by injector.instance<Authority>()
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

        withTestApplication({ configureRouting(application); configureContentNegotiation() }) {
            with(handleRequest(HttpMethod.Put, "/entity/${resourceEntity.entityId}"){
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(Json.encodeToString(entity))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val entityResult = Json.decodeFromString<EntityResult>(response.content!!)

                assertEquals(entity.entityId, entityResult.entityId)
                assertEquals(entity.name, entityResult.summary.name)
                assertEquals(entity.description, entityResult.summary.description)
                assertEquals(entity.imageUri, entityResult.summary.imageUri)
            }
        }
    }
}