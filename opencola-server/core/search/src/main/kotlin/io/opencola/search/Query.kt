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

data class Query(val queryString: String, val terms: List<String>, val authorityIds: Set<Id> = emptySet(), val tags: Set<String> = emptySet()) {
    override fun toString(): String {
        return "Query(queryString='$queryString', authorityIds=${authorityIds} tags=$tags, terms=$terms)"
    }
}