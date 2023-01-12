package io.opencola.core.search

import io.opencola.core.model.CoreAttribute
import io.opencola.core.serialization.ByteArrayCodec
import io.opencola.core.serialization.codecs.StringByteArrayCodec

fun getLuceneQueryString(query: String) : String {
    // TODO: play with fuzzy search (append ~ to terms) and support phrase "term1 term2" queries
    // https://www.lucenetutorial.com/lucene-query-syntax.html
    return CoreAttribute.values()
        .map { it.spec }
        // TODO: Fix this hack that identifies text search fields
        .filter { it.isIndexable && it.codec == StringByteArrayCodec as ByteArrayCodec<*> }
        .joinToString(" ") { "${it.name}:\"$query\"~10000" }

}