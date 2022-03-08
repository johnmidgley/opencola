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
import opencola.core.model.Authority
import opencola.core.model.Entity
import opencola.core.model.Fact
import opencola.core.model.ResourceEntity
import opencola.core.storage.EntityStore
import org.kodein.di.instance
import java.net.URI
import java.net.URLEncoder

class ApplicationTest {
    init{
        // Ensure test application is initialized.
        TestApplication.instance
    }

    @Test
    fun testRoot() {
        withTestApplication({ configureRouting() }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun testGetEntity(){
        val injector = TestApplication.instance.injector
        val authority by injector.instance<Authority>()
        val entityStore by injector.instance<EntityStore>("Shared")
        val entity = ResourceEntity(authority.authorityId, URI("http://opencola.org"), trust = 1.0F, like = true, rating = 1.0F)

        entityStore.commitChanges(entity)

        withTestApplication({ configureRouting(); configureContentNegotiation() }) {
            handleRequest(HttpMethod.Get, "/entity/${entity.entityId}").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.content)
                val facts = Json.decodeFromString<List<Fact>>(response.content!!)
                val returnedEntity = Entity.getInstance(facts) as? ResourceEntity
                assertNotNull(returnedEntity)
                // TODO: Can't use .equals, since returned entity has committed transaction ids.
                // Make commit return the updated entity or implement a contentEquals that ignores transaction id
                assertEquals(entity.authorityId, returnedEntity.authorityId)
                assertEquals(entity.entityId, returnedEntity.entityId)
                assertEquals(entity.uri, returnedEntity.uri)
                assertEquals(entity.trust, returnedEntity.trust)
                assertEquals(entity.like, returnedEntity.like)
                assertEquals(entity.rating, returnedEntity.rating)
            }
        }
    }

    @Test
    fun testStatusActions(){
        val injector = TestApplication.instance.injector
        val authority by injector.instance<Authority>()
        val entityStore by injector.instance<EntityStore>("Shared")
        val uri = URI("https://opencola.org")
        val entity = ResourceEntity(authority.authorityId, uri, trust = 1.0F, like = true, rating = 1.0F)

        entityStore.commitChanges(entity)

        withTestApplication({ configureRouting(); configureContentNegotiation() }) {
            handleRequest(HttpMethod.Get, "/status/${URLEncoder.encode(uri.toString(), "utf-8")}").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.content)
                val actions = Json.decodeFromString<StatusHandler.Actions>(response.content!!)

                assertEquals(entity.trust, actions.trust)
                assertEquals(entity.like, actions.like)
                assertEquals(entity.rating, actions.rating)
            }
        }
    }

    @Test
    // TODO: Break this up!
    fun testSavePageThenSearch(){
        val mhtPath = TestApplication.instance.path.resolve("../sample-docs/Conway's Game of Life - Wikipedia.mht")

        withTestApplication({ configureRouting(); configureContentNegotiation() }) {
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
}