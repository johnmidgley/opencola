package io.opencola.search

import io.opencola.model.CoreAttribute
import io.opencola.model.Id
import io.opencola.model.value.StringValue

fun getAuthorityIdsQuery(authorityIds: Set<Id>): String {
    return authorityIds.joinToString(" OR ") { "authorityId:\"$it\"" }
}

/**
 * Return a lucene query string for the given [query].
 */
fun getLuceneQueryString(query: Query): String {
    // TODO: Find / build and use a proper lucene query builder
    val luceneQuery = StringBuilder()

    val authorityIdsQuery = getAuthorityIdsQuery(query.authorityIds)
    if(authorityIdsQuery.isNotEmpty())
        luceneQuery.append("($authorityIdsQuery)")

    val tagsQuery = query.tags.joinToString(" AND ") { "tags:\"$it\"" }
    if (tagsQuery.isNotEmpty()) {
        if (luceneQuery.isNotEmpty()) {
            luceneQuery.append(" AND ")
        }
        luceneQuery.append(tagsQuery)
    }

    if(query.terms.isNotEmpty()) {
        val termsQuery = CoreAttribute.values()
            .map { it.spec }
            // TODO: Fix this hack that identifies text search fields
            .filter { it.isIndexable && it.valueWrapper.javaClass == StringValue.Wrapper::class.java }
            .joinToString(" ") { "${it.name}:\"${query.terms.joinToString(" ")}\"~10000" }
        luceneQuery.append(
            if (luceneQuery.isEmpty())
                termsQuery
            else
                " AND ($termsQuery)"
        )
    }

    return luceneQuery.toString().also { println("Lucene Q: $it") }
}