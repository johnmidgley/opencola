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
import io.opencola.util.Base58

class Context(val personaIds: Set<Id>) {
    constructor(context: String?) : this (
         context?.let { String(Base58.decode(it)) }
            ?.split(",")
            ?.filter{ it.isNotBlank() }
            ?.map { Id.decode(it) }
            ?.toSet()
            ?: emptySet()
    )

    constructor(vararg personaIds: Id) : this(personaIds.toSet())

    override fun toString(): String {
        return personaIds.joinToString(",").toByteArray().let { Base58.encode(it) }
    }
}