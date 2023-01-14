package io.opencola.search

import io.opencola.model.Entity
import io.opencola.model.Id

interface SearchIndex {
    fun create()
    fun destroy()
    fun add(entity: Entity)
    fun delete(authorityId: Id, entityId: Id)
    fun search(query: String): List<SearchResult>
}
