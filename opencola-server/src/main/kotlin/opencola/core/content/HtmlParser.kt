package opencola.core.content

import org.jsoup.Jsoup

// Likely will want to use JSoup to do some HTML parsing
// https://www.baeldung.com/java-with-jsoup
class HtmlParser(html: String) {
    private val doc = Jsoup.parse(html)

    fun parseDescription() : String? {
        val metaDescriptionNames = listOf("description", "og:description", "twitter:description")
        return doc.select("meta[name]")
            .filter { metaDescriptionNames.contains(it.attributes()["name"]) }
            .sortedBy { metaDescriptionNames.indexOf(it.attributes()["name"]) }
            .map{ it.attributes()["content"] }
            .firstOrNull()
    }
}