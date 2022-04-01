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

    private fun firstNonEmptyParagraphText(cssQuery: String): String? {
        return doc.select(cssQuery)
            .firstOrNull()?.select("p")
            ?.firstOrNull { it.text().isNotEmpty() }
            ?.text()
    }

    private fun firstNonEmptyParagraphText(cssQueries: List<String>) : String? {
        return cssQueries.mapNotNull { firstNonEmptyParagraphText(it) }.firstOrNull()
    }

    // TODO - make meta content lists configurable
    fun parseDescription(): String? {
        return selectMetaContent(listOf("meta[name=description]", "meta[property=og:description]", "meta[name=twitter:description]")).firstOrNull()
            ?: firstNonEmptyParagraphText(listOf("#storytext", "#content", "main", "body"))

    }

    fun parseImageUri(): URI? {
        return selectMetaContent(listOf("meta[name=image]", "meta[property=og:image]", "meta[name=twitter:image]", "meta[name=twitter:image:src]"))
            .mapNotNull { it.tryParseUri() }.firstOrNull { it.isAbsolute }
    }
}