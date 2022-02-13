package opencola.service.search

import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore
import opencola.server.SearchResult
import opencola.server.SearchResults

class SearchService(val entityStore: EntityStore, private val searchIndex: SearchIndex) {
    fun search(query: String): SearchResults {
        val results = searchIndex.search(query).map { SearchResult(it.entityId.toString(), it.name, it.description) }
        return SearchResults(query, results)
    }
}