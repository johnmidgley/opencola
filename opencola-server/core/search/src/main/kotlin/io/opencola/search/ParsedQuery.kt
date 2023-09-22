package io.opencola.search

data class ParsedQuery(val query: String, val tags: Set<String>, val terms: List<String>) {
    override fun toString(): String {
        return "ParsedQuery(query='$query', tags=$tags, terms=$terms)"
    }

    fun termsAsString(): String {
        return terms.joinToString(" ")
    }

    fun tagsAsString(): String {
        return tags.joinToString(" ")
    }
}