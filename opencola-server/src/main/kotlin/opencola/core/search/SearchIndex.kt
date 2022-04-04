package opencola.core.search

import opencola.core.model.Entity

interface SearchIndex {
    fun create()
    fun delete()
    fun index(entity: Entity)
    fun search(query: String): List<SearchResult>
}
