package opencola.server

import io.ktor.http.*
import io.ktor.http.content.*
import kotlin.test.*
import io.ktor.server.testing.*
import io.ktor.utils.io.streams.*
import kotlinx.serialization.json.Json
import opencola.core.TestApplication
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureRouting
import opencola.service.search.SearchResults
import java.io.File
import kotlinx.serialization.decodeFromString
import opencola.core.model.*
import opencola.core.storage.EntityStore
import org.kodein.di.instance
import java.net.URI
import java.net.URLEncoder
import kotlinx.serialization.encodeToString
import opencola.core.network.PeerRouter
import opencola.core.security.generateKeyPair
import opencola.core.storage.AddressBook
import opencola.server.handlers.FeedResult
import opencola.service.EntityResult

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
                assertEquals(entity.entityId, Id.fromHexString(entityResult.entityId))
                assertEquals(entity.uri, URI(entityResult.summary.uri))

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

            handleRequest(HttpMethod.Get, "/search?q=game").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val searchResults = Json.decodeFromString<SearchResults>(response.content!!)
                assertEquals("Conway's Game of Life - Wikipedia", searchResults.matches.first().name)
            }
        }
    }

    @Test
    fun testPostNotification(){
        val localAuthority by injector.instance<Authority>()
        val addressBook by injector.instance<AddressBook>()
        val peerAuthority = addressBook.putAuthority(Authority(localAuthority.authorityId, generateKeyPair().public, URI(""), "Test"))
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

    @Test
    fun testGetFeed(){
        val authority by injector.instance<Authority>()
        val entityStore by injector.instance<EntityStore>()
        entityStore.resetStore()

        val uri = URI("https://opencola.org/${Id.new()}")
        val entity = ResourceEntity(authority.authorityId, uri)
        entity.dataId = Id.new()
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
                assertEquals(entity.dataId.toString(), feedResult.results[0].activities[0].actions[0].id)
                assertEquals(entity.trust.toString(), feedResult.results[0].activities[1].actions[0].value)
                assertEquals(entity.like.toString(), feedResult.results[0].activities[2].actions[0].value)
                assertEquals(entity.rating.toString(), feedResult.results[0].activities[3].actions[0].value)
                assertEquals(comment.text, feedResult.results[0].activities[4].actions[0].value)
            }
        }
    }

    // @Test
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
        val entity = EntityResult(
            resourceEntity.entityId,
            EntityResult.Summary("Name1", "", "Description1", "https://opencola.io/image1.png"),
            emptyList())


        withTestApplication({ configureRouting(application); configureContentNegotiation() }) {
            with(handleRequest(HttpMethod.Post, "/entity/${resourceEntity.entityId}"){
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(Json.encodeToString(entity))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val entityResult = Json.decodeFromString<EntityResult>(response.content!!)

                assertEquals(entity.entityId, entityResult.entityId)
                assertEquals(entity.summary.name, entityResult.summary.name)
                assertEquals(entity.summary.description, entityResult.summary.description)
                assertEquals(entity.summary.imageUri, entityResult.summary.imageUri)
            }
        }
    }
}