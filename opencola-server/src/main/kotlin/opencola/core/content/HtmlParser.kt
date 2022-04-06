package opencola.core.content

import opencola.core.extensions.tryParseUri
import org.jsoup.Jsoup
import java.net.URI

// Likely will want to use JSoup to do some HTML parsing
// https://www.baeldung.com/java-with-jsoup
class HtmlParser(html: String) {
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
}