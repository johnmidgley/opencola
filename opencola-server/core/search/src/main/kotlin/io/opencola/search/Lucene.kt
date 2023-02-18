package io.opencola.search

import io.opencola.model.CoreAttribute
import io.opencola.model.Id
import io.opencola.serialization.ByteArrayCodec
import io.opencola.serialization.codecs.StringByteArrayCodec

fun getLuceneQueryString(authorityIds: List<Id>, query: String) : String {
    // TODO: play with fuzzy search (append ~ to terms) and support phrase "term1 term2" queries
    // https://www.lucenetutorial.com/lucene-query-syntax.html
    return CoreAttribute.values()
        .map { it.spec }
        // TODO: Fix this hack that identifies text search fields
        .filter { it.isIndexable && it.codec == StringByteArrayCodec as ByteArrayCodec<*> }
        .joinToString(" ") { "${it.name}:\"$query\"~10000" }

}