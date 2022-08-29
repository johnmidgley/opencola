package opencola.server.handlers

import kotlinx.serialization.Serializable
import opencola.core.model.Authority
import opencola.core.model.ResourceEntity
import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore

@Serializable
data class SearchResult(val id: String, val name: String?, val uri: String, val description: String?)

@Serializable
data class SearchResults(val query: String, val matches: List<SearchResult>)

fun handleSearch(authority: Authority, entityStore: EntityStore, searchIndex: SearchIndex, query: String) : SearchResults {
    val results = searchIndex.search(query).map {
        when (val entity = entityStore.getEntity(it.authorityId, it.entityId)){
            is ResourceEntity -> SearchResult(it.entityId.toString(), it.name, entity.uri.toString(), it.description)
            else -> SearchResult(it.entityId.toString(), it.name, "/entity/${it.authorityId}/${it.entityId}", it.description)
        }
    }
    return SearchResults(query, results)
}