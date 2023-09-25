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
        query: Query,
        maxResults: Int,
        pagingToken: String? = null
    ): SearchResults

    fun getAllResults(query: Query, resultBlockSize: Int = 100) = sequence {
        var token: String? = null
        do {
            val results = getResults(query, resultBlockSize, token)
            results.items.forEach { yield(it) }
            token = results.pagingToken
        } while (token != null)
    }
}
