package io.opencola.search

import io.opencola.model.Id

data class Query(val queryString: String, val terms: List<String>, val authorityIds: Set<Id> = emptySet(), val tags: Set<String> = emptySet()) {
    override fun toString(): String {
        return "Query(queryString='$queryString', authorityIds=${authorityIds} tags=$tags, terms=$terms)"
    }
}