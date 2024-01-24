/*
 * Copyright 2024 OpenCola
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

package io.opencola.search

import io.opencola.model.Id
import io.opencola.storage.addressbook.AddressBook

class QueryParser(val addressBook: AddressBook) {
    private fun getAuthorityIds(names: Set<String>): Set<Id> {
        if (names.isEmpty()) return emptySet()

        val matches = addressBook.getEntries()
            .flatMap { entry ->
                names.mapNotNull { name ->
                    if (entry.name.lowercase().contains(name.lowercase())) entry.entityId else null
                }
            }

        return if (matches.isEmpty()) setOf(Id.EMPTY) else matches.toSet()
    }

    fun parse(query: String, defaultAuthorityIds: Set<Id> = emptySet()): Query {
        val components = query
            .split(" ")
            .filter { it.isNotBlank() }
            .groupBy {
                when (it.first()) {
                    '@' -> "authorities"
                    '#' -> "tags"
                    else -> "terms"
                }
            }

        val authorities = components["authorities"]?.map { it.substring(1) }?.toSet() ?: emptySet()
        val queryAuthorityIds = getAuthorityIds(authorities).let { it.ifEmpty { defaultAuthorityIds } }
        val tags = components["tags"]?.map { it.substring(1) }?.toSet() ?: emptySet()
        val terms = components["terms"] ?: emptyList()

        return Query(query, terms, queryAuthorityIds, tags)
    }
}