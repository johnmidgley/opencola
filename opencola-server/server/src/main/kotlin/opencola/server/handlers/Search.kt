package opencola.server.handlers

import io.opencola.model.Id
import kotlinx.serialization.Serializable
import io.opencola.model.ResourceEntity
import io.opencola.search.SearchIndex
import io.opencola.storage.EntityStore

@Serializable
data class SearchResult(val id: String, val name: String?, val uri: String, val description: String?)

@Serializable
data class SearchResults(val query: String, val matches: List<SearchResult>)

fun handleSearch(entityStore: EntityStore, searchIndex: SearchIndex, authorityIds: Set<Id>, query: String) : SearchResults {
    // TODO: Make search index take authority as parameter
    val results = searchIndex.getResults(query, 100, authorityIds, null).items.map {
        when (val entity = entityStore.getEntity(it.authorityId, it.entityId)){
            is ResourceEntity -> SearchResult(it.entityId.toString(), it.name, entity.uri.toString(), it.description)
            else -> SearchResult(it.entityId.toString(), it.name, "/entity/${it.authorityId}/${it.entityId}", it.description)
        }
    }
    return SearchResults(query, results)
}