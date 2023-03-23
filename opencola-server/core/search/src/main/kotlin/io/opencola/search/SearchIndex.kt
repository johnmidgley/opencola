package io.opencola.search

import io.opencola.model.Entity
import io.opencola.model.Id

interface SearchIndex {
    // TODO: remove
    fun create()
    fun destroy()

    // TODO: change to addEntities, deleteEntities (with vararg entity Ids), getResults, getAllResults
    fun add(entity: Entity) // TODO: Make varargs
    fun delete(authorityId: Id, entityId: Id)
    fun search(
        query: String,
        maxResults: Int,
        authorityIds: Set<Id> = emptySet(),
        pagingToken: String? = null
    ): SearchResults

    fun search(query: String, resultBlockSize: Int = 100, authorityIds: Set<Id> = emptySet()) = sequence {
        var token: String? = null
        do {
            val results = search(query, resultBlockSize, authorityIds, token)
            results.items.forEach { yield(it) }
            token = results.pagingToken
        } while (token != null)
    }
}
