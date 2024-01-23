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

import io.opencola.model.Attribute
import io.opencola.model.AttributeType.*
import io.opencola.model.Entity
import io.opencola.model.Id
import io.opencola.security.hash.Sha256Hash

abstract class AbstractSearchIndex : SearchIndex {
    protected val logger = mu.KotlinLogging.logger("SearchIndex")

    protected fun getDocId(authorityId: Id, entityId: Id): String {
        // For some reason this doesn't work if base58 is used (testLuceneRepeatIndexing fails). Are there restrictions on doc ids?
        return Sha256Hash.ofString("${authorityId}:${entityId}").toHexString()
    }

    fun getAttributeAsText(entity: Entity, attribute: Attribute): String? {
        return when (attribute.type) {
            SingleValue -> entity.getValue(attribute.name)?.get()?.toString()

            MultiValueSet -> entity.getSetValues(attribute.name)
                .ifEmpty { null }
                ?.joinToString { it.get().toString() }

            MultiValueList -> entity.getListValues(attribute.name)
                .ifEmpty { null }
                ?.joinToString { it.get().toString() }
        }
    }
}