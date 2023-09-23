package io.opencola.search

import io.opencola.model.Id

data class ParsedQuery(val query: String, val authorityIds: Set<Id>, val tags: Set<String>, val terms: List<String>) {
    override fun toString(): String {
        return "ParsedQuery(query='$query', authorityIds=${authorityIds} tags=$tags, terms=$terms)"
    }

    fun authorityIdsAsString(): String {
        return authorityIds.joinToString(" ")
    }

    fun termsAsString(): String {
        return terms.joinToString(" ")
    }

    fun tagsAsString(): String {
        return tags.joinToString(" ")
    }
}