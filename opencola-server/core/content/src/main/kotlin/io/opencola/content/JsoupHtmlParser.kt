/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.content

import io.opencola.util.tryParseUri
import org.jsoup.Jsoup
import java.net.URI

// Likely will want to use JSoup to do some HTML parsing
// https://www.baeldung.com/java-with-jsoup
// TODO: Make interface
class JsoupHtmlParser(html: String) {
    private val doc = Jsoup.parse(html)

    private fun selectMetaContent(selectors: List<String>): List<String> {
        return selectors
            .flatMap { s -> doc.select(s).map { it.attributes()["content"] } }
            .filter { it.isNotEmpty() }
    }

    private fun firstNonEmptyElementText(cssQuery: String, elementQuery: String): String? {
        return doc.select(cssQuery)
            .firstOrNull()?.select(elementQuery)
            ?.firstOrNull { it.text().isNotEmpty() }
            ?.text()
    }

    private fun firstNonEmptyElementText(cssQueries: List<String>, elementQuery: String): String? {
        return cssQueries.firstNotNullOfOrNull { firstNonEmptyElementText(it, elementQuery) }
    }

    fun parseTitle(): String? {
        return doc.select("title").firstOrNull()?.text() ?: selectMetaContent(
            listOf(
                "meta[property=og:title]",
                "meta[name=twitter:title]",
            )
        ).firstOrNull()
    }

    // TODO - make meta content lists configurable
    fun parseDescription(): String? {
        return selectMetaContent(
            listOf(
                "meta[name=description]",
                "meta[property=og:description]",
                "meta[name=twitter:description]"
            )
        ).firstOrNull()
            ?: firstNonEmptyElementText(listOf("#storytext", "#content", "main", "body"), "p")

    }

    fun parseImageUri(): URI? {
        return selectMetaContent(
            listOf(
                "meta[name=image]",
                "meta[property=og:image]",
                "meta[name=twitter:image]",
                "meta[name=twitter:image:src]"
            )
        )
            .mapNotNull { it.tryParseUri() }.firstOrNull { it.isAbsolute }
    }

    fun parseEmbeddedType() : String? {
        val singleChild = doc.body().children().singleOrNull()

        if(singleChild != null && singleChild.normalName() == "embed") {
            return singleChild.attr("type")
        }

        return null
    }

    fun parseText(): String? {
        return doc.body().text().ifBlank { null }
    }
}