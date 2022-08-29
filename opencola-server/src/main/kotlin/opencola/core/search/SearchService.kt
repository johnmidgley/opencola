package opencola.core.search

import opencola.core.model.Authority
import opencola.core.model.ResourceEntity
import opencola.core.storage.EntityStore
import opencola.service.search.SearchResult
import opencola.service.search.SearchResults

class SearchService(val authority: Authority, val entityStore: EntityStore, private val searchIndex: SearchIndex) {
    fun search(query: String): SearchResults {
        val results = searchIndex.search(query).map {
            when (val entity = entityStore.getEntity(it.authorityId, it.entityId)){
                is ResourceEntity -> SearchResult(it.entityId.toString(), it.name, entity.uri.toString(), it.description)
                else -> SearchResult(it.entityId.toString(), it.name, "/entity/${it.authorityId}/${it.entityId}", it.description)
            }
        }
        return SearchResults(query, results)
    }
}