package io.opencola.search

import io.opencola.model.Id
import io.opencola.storage.addressbook.AddressBook

class QueryParser(val addressBook: AddressBook) {
    private fun getAuthorityIds(names: Set<String>): Set<Id> {
        if (names.isEmpty()) return emptySet()

        val matches = addressBook.getEntries()
            .flatMap { entry ->
                names.mapNotNull { name ->
                    if (entry.name.lowercase().contains(name.lowercase())) entry.entityId else null
                }
            }

        return if (matches.isEmpty()) setOf(Id.EMPTY) else matches.toSet()
    }

    fun parse(query: String, defaultAuthorityIds: Set<Id> = emptySet()): Query {
        val components = query
            .split(" ")
            .filter { it.isNotBlank() }
            .groupBy {
                when (it.first()) {
                    '@' -> "authorities"
                    '#' -> "tags"
                    else -> "terms"
                }
            }

        val authorities = components["authorities"]?.map { it.substring(1) }?.toSet() ?: emptySet()
        val queryAuthorityIds = getAuthorityIds(authorities).let { it.ifEmpty { defaultAuthorityIds } }
        val tags = components["tags"]?.map { it.substring(1) }?.toSet() ?: emptySet()
        val terms = components["terms"] ?: emptyList()

        return Query(query, terms, queryAuthorityIds, tags)
    }
}