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