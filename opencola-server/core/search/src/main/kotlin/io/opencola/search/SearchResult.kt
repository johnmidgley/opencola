package io.opencola.search

import io.opencola.model.Id


class SearchResults(val pagingToken: String?, val items: List<SearchResult>)

// TODO: Add highlighting
// TODO: Could reduce to just authorityId and entityId
data class SearchResult(val authorityId: Id, val entityId: Id, val name: String?, val description: String?)