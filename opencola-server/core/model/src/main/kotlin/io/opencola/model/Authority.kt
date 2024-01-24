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

import io.opencola.security.encode
import java.net.URI
import java.security.PublicKey

open class Authority : Entity {
    var uri by uriAttributeDelegate
    var publicKey by publicKeyAttributeDelegate
    var networkToken by byteArrayAttributeDelegate

    // TODO - Remove optional parameters. Object is mutable so .also{} serves the same purpose without the mess and maintenance
    constructor(
        authorityId: Id,
        publicKey: PublicKey, // TODO: Id should not depend on public key, since public key may change
        uri: URI,
        name: String,
        description: String? = null,
        text: String? = null,
        imageUri: URI? = null,
        trust: Float? = null,
        like: Boolean? = null,
        rating: Float? = null,
        tags: List<String>? = null,
    ) : super(authorityId, Id.ofPublicKey(publicKey), name, description, text, imageUri, trust, like, rating, tags) {
        this.uri = uri
        this.publicKey = publicKey
    }

        constructor(
        publicKey: PublicKey,
        uri: URI,
        name: String,
        description: String? = null,
        text: String? = null,
        imageUri: URI? = null,
        trust: Float? = null,
        like: Boolean? = null,
        rating: Float? = null,
        tags: List<String>? = null,
    ) : this(Id.ofPublicKey(publicKey), publicKey, uri, name, description, text, imageUri, trust, like, rating, tags)

    constructor(facts: List<Fact>) : super(facts)

    override fun toString(): String {
        return "{ authority=$authorityId, entityId=$entityId, name=$name, publicKey=${publicKey?.encode()}, uri=$uri }"
    }

    // TODO: Make all code locations use this
    private val activeTag = "active"

    fun setActive(active: Boolean) {
        tags = if(active)
            tags.plus(activeTag)
        else
            tags.minus(activeTag)
    }

    fun getActive() : Boolean {
        return tags.contains(activeTag)
    }
}