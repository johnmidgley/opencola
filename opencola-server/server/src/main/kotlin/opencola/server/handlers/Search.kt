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

package opencola.server.handlers

import io.opencola.model.Id
import kotlinx.serialization.Serializable
import io.opencola.model.ResourceEntity
import io.opencola.search.QueryParser
import io.opencola.search.SearchIndex
import io.opencola.storage.entitystore.EntityStore

@Serializable
data class SearchResult(val id: String, val name: String?, val uri: String, val description: String?)

@Serializable
data class SearchResults(val query: String, val matches: List<SearchResult>)

fun handleSearch(entityStore: EntityStore, queryParser: QueryParser, searchIndex: SearchIndex, authorityIds: Set<Id>, query: String) : SearchResults {
    // TODO: Make search index take authority as parameter
    val results = searchIndex.getResults(queryParser.parse(query, authorityIds), 100, null).items.map {
        when (val entity = entityStore.getEntity(it.authorityId, it.entityId)){
            is ResourceEntity -> SearchResult(it.entityId.toString(), it.name, entity.uri.toString(), it.description)
            else -> SearchResult(it.entityId.toString(), it.name, "/entity/${it.authorityId}/${it.entityId}", it.description)
        }
    }
    return SearchResults(query, results)
}