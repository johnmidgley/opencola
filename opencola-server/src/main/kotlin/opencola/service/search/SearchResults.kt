package opencola.service.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(val id: String, val name: String?, val uri: String, val description: String?)

@Serializable
data class SearchResults(val query: String, val matches: List<SearchResult>)