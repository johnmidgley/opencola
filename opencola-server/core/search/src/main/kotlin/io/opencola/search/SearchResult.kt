package io.opencola.search

import io.opencola.model.Id


class SearchResults(val pagingToken: String?, val items: List<SearchResult>)

// TODO: Add highlighting
// TODO: Should probably return full entity, but somewhat expensive, and won't be compatible with highlighting
class SearchResult(val authorityId: Id, val entityId: Id, val name: String?, val description: String?)