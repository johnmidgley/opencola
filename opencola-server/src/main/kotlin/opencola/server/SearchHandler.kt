package opencola.server

import io.ktor.application.*
import io.ktor.response.*
import opencola.server.config.App

class SearchHandler(private val call: ApplicationCall) {
    suspend fun respond(){
        val query = call.request.queryParameters["q"] ?: return call.respond(SearchResults("", emptyList()))
        val results = App.searchService.search(query).map { SearchResult(it.entityId.toString(), it.name, it.description) }
        call.respond(SearchResults(query, results))
    }
}