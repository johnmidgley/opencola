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

package io.opencola.model

import java.net.URI

class PostEntity : Entity {
    constructor(authorityId: Id,
                entityId: Id,
                name: String? = null,
                description: String? = null,
                text: String? = null,
                imageUri: URI? = null,
                trust: Float? = null,
                tags: List<String>? = null,
                like: Boolean? = null,
                rating: Float? = null,
    ) : super(authorityId, entityId, name, description, text, imageUri, trust, like, rating, tags)

    constructor(authorityId: Id,
                name: String? = null,
                description: String? = null,
                text: String? = null,
                imageUri: URI? = null,
                trust: Float? = null,
                tags: List<String>? = null,
                like: Boolean? = null,
                rating: Float? = null
    ) : this(authorityId, Id.new(), name, description, text, imageUri, trust, tags, like, rating)

    constructor(facts: List<Fact>) : super(facts)
}