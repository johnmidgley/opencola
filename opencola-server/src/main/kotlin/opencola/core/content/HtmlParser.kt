package opencola.core.content

import opencola.core.extensions.tryParseUri
import org.jsoup.Jsoup
import java.net.URI

// Likely will want to use JSoup to do some HTML parsing
// https://www.baeldung.com/java-with-jsoup
class HtmlParser(html: String) {
    private val doc = Jsoup.parse(html)

    private fun selectMetaContent(names: List<String>): List<String> {
        return doc.select("meta[name]")
            .filter{ names.contains(it.attributes()["name"]) }
            .sortedBy { names.indexOf(it.attributes()["name"]) }
            .map { it.attributes()["content"] }
    }

    fun parseDescription() : String? {
        return selectMetaContent(listOf("description", "og:description", "twitter:description"))
            .firstOrNull()
    }

    fun parseImageUri() : URI? {
        return selectMetaContent(listOf("image", "og:image", "twitter:image"))
            .mapNotNull { it.tryParseUri() }
            .firstOrNull()
    }
}