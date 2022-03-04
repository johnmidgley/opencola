package opencola.server

import io.ktor.http.*
import io.ktor.http.content.*
import kotlin.test.*
import io.ktor.server.testing.*
import io.ktor.utils.io.streams.*
import kotlinx.serialization.json.Json
import opencola.core.TestApplication
import opencola.core.config.Application
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureRouting
import opencola.service.search.SearchResults
import java.io.File
import kotlinx.serialization.decodeFromString

class ApplicationTest {
    init{
        TestApplication.init()
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
    // TODO: Break this up!
    fun testSavePage(){
        val mhtPath = Application.instance.path.resolve("../sample-docs/Conway's Game of Life - Wikipedia.mht")

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