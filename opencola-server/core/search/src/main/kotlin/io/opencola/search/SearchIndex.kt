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

import io.opencola.model.Entity
import io.opencola.model.Id

interface SearchIndex {
    // TODO: remove
    fun create()
    fun destroy()

    // TODO: change to addEntities, deleteEntities (with vararg entity Ids),
    fun addEntities(vararg entities: Entity)
    fun deleteEntities(authorityId: Id, vararg entityIds: Id)
    fun getResults(
        query: Query,
        maxResults: Int,
        pagingToken: String? = null
    ): SearchResults

    fun getAllResults(query: Query, resultBlockSize: Int = 100) = sequence {
        var token: String? = null
        do {
            val results = getResults(query, resultBlockSize, token)
            results.items.forEach { yield(it) }
            token = results.pagingToken
        } while (token != null)
    }
}
