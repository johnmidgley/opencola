package io.opencola.search

import io.opencola.model.Entity
import io.opencola.model.Id

interface SearchIndex {
    // TODO: remove
    fun create()
    fun destroy()

    // TODO: change to addEntities, deleteEntities (with vararg entity Ids),
    fun addEntities(vararg entities: Entity)
    fun deleteEntities(authorityId: Id, vararg entityIds: Id)
    fun getResults(
        query: String,
        maxResults: Int,
        authorityIds: Set<Id> = emptySet(),
        pagingToken: String? = null
    ): SearchResults

    fun getAllResults(query: String, resultBlockSize: Int = 100, authorityIds: Set<Id> = emptySet()) = sequence {
        var token: String? = null
        do {
            val results = getResults(query, resultBlockSize, authorityIds, token)
            results.items.forEach { yield(it) }
            token = results.pagingToken
        } while (token != null)
    }
}
