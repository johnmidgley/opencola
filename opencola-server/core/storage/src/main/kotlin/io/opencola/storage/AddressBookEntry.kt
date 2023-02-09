package io.opencola.storage

import io.opencola.model.Authority
import io.opencola.model.Id
import java.net.URI
import java.security.PublicKey

open class AddressBookEntry(
    val personaId: Id,
    val entityId: Id,
    val name: String,
    val publicKey: PublicKey,
    val address: URI,
    val imageUri: URI?,
    val isActive: Boolean,
) {
    constructor(authority: Authority) : this(
        authority.authorityId,
        authority.entityId,
        authority.name!!,
        authority.publicKey!!,
        authority.uri!!,
        authority.imageUri,
        authority.getActive()
    )

    init {
        require(address.scheme != null) { "Address must have scheme" }
    }

    override fun toString(): String {
         return "AddressBookEntry(personaId=$personaId, entityId=$entityId, name='$name', publicKey=${Id.ofPublicKey(publicKey)}, address=$address, imageUri=$imageUri, isActive=$isActive)"
    }

    @Suppress("DuplicatedCode")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AddressBookEntry

        if (personaId != other.personaId) return false
        if (entityId != other.entityId) return false
        if (name != other.name) return false
        if (publicKey != other.publicKey) return false
        if (address != other.address) return false
        if (imageUri != other.imageUri) return false
        if (isActive != other.isActive) return false

        return true
    }
    override fun hashCode(): Int {
        var result = personaId.hashCode()
        result = 31 * result + entityId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + (imageUri?.hashCode() ?: 0)
        result = 31 * result + isActive.hashCode()
        return result
    }
}