package opencola.core.search

import opencola.core.model.Entity
import opencola.core.model.Id

interface SearchIndex {
    fun create()
    fun destroy()
    fun add(entity: Entity)
    fun delete(authorityId: Id, entityId: Id)
    fun search(query: String): List<SearchResult>
}
