package io.opencola.search

import io.opencola.model.CoreAttribute
import io.opencola.model.Id
import io.opencola.model.value.StringValue

/**
 * Return a lucene query string for the given [query].
 */
fun getLuceneQueryString(query: ParsedQuery): String {
    val tagsQuery = query.tagsAsString().let { if(it.isEmpty()) "" else "tags:\"$it\""}
    val termsQuery =  CoreAttribute.values()
        .map { it.spec }
        // TODO: Fix this hack that identifies text search fields
        .filter { it.isIndexable && it.valueWrapper.javaClass == StringValue.Wrapper::class.java }
        .joinToString(" ") { "${it.name}:\"${query.termsAsString()}\"~10000" }

    return if(tagsQuery.isEmpty())
        termsQuery
    else
        "$tagsQuery AND ($termsQuery)"
}

/**
 * Return a lucene query string for the given [authorityIds].
 */
fun getLuceneQueryString(authorityIds: Set<Id>): String {
    return authorityIds.joinToString(" OR ") { "authorityId:\"$it\"" }
}

/**
 * Returns a lucene query string for the given [query] and [authorityIds].
 * The query string is used to search the lucene index.
 */
fun getLuceneQueryString(authorityIds: Set<Id>, query: ParsedQuery): String {
    // https://www.lucenetutorial.com/lucene-query-syntax.html
    val baseQuery = if (authorityIds.isEmpty()) "" else "(${getLuceneQueryString(authorityIds)}) AND"
    return "$baseQuery (${getLuceneQueryString(query)})".also { println("Lucene Query: $it") }
}