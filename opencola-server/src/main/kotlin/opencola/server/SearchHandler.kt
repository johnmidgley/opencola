package opencola.server

import io.ktor.application.*
import io.ktor.response.*
import opencola.core.config.Application
import opencola.core.search.SearchService
import org.kodein.di.instance

class SearchHandler(private val call: ApplicationCall) {
    // TODO: This injector is hiding the dependency. Move to constructor param, and move call to respond method
    private val searchService by Application.instance.injector.instance<SearchService>()

    suspend fun respond(){
        val query = call.request.queryParameters["q"] ?: throw IllegalArgumentException("No query (q) specified in parameters")
        val results = searchService.search(query).map { SearchResult(it.entityId.toString(), it.name, it.description) }
        call.respond(SearchResults(query, results))
    }
}
