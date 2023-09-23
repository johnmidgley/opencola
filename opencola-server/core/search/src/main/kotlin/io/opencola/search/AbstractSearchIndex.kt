package io.opencola.search

import io.opencola.util.nullOrElse
import io.opencola.model.Attribute
import io.opencola.model.AttributeType.*
import io.opencola.model.Entity
import io.opencola.model.Id
import io.opencola.security.hash.Sha256Hash
import io.opencola.storage.addressbook.AddressBook

abstract class AbstractSearchIndex(private val addressBook: AddressBook) : SearchIndex {
    protected val logger = mu.KotlinLogging.logger("SearchIndex")

    protected fun getDocId(authorityId: Id, entityId: Id): String {
        // For some reason this doesn't work if base58 is used (testLuceneRepeatIndexing fails). Are there restrictions on doc ids?
        return Sha256Hash.ofString("${authorityId}:${entityId}").toHexString()
    }

    fun getAttributeAsText(entity: Entity, attribute: Attribute): String? {
        return when (attribute.type) {
            SingleValue -> entity.getValue(attribute.name)
                .nullOrElse { it.get().toString() }

            MultiValueSet -> entity.getSetValues(attribute.name)
                .ifEmpty { null }
                ?.joinToString { it.get().toString() }

            MultiValueList -> entity.getListValues(attribute.name)
                .ifEmpty { null }
                ?.joinToString { it.get().toString() }
        }
    }

    private fun getAuthorityIds(names: Set<String>): Set<Id> {
        if(names.isEmpty()) return emptySet()

        val matches = addressBook.getEntries()
            .flatMap { entry ->
                names.mapNotNull { name ->
                    if (entry.name.lowercase().contains(name.lowercase())) entry.entityId else null
                }
            }

        return if (matches.isEmpty()) setOf(Id.EMPTY) else matches.toSet()
    }

    fun parseQuery(authorityIds: Set<Id>, query: String): ParsedQuery {
        val components = query.split(" ").groupBy {
            when (it.first()) {
                '@' -> "authorities"
                '#' -> "tags"
                else -> "terms"
            }
        }

        val authorities = components["authorities"]?.map { it.substring(1) }?.toSet() ?: emptySet()
        val queryAuthorityIds = getAuthorityIds(authorities)
        val tags = components["tags"]?.map { it.substring(1) }?.toSet() ?: emptySet()
        val terms = components["terms"] ?: emptyList()


        // val (tags, terms) = query.split(" ").partition { it.startsWith("#") }
        return ParsedQuery(query, queryAuthorityIds.ifEmpty { authorityIds }, tags, terms)
            .also { logger.info { "Parsed query: $it" } }
    }
}