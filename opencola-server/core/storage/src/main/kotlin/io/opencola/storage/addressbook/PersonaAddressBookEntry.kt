package io.opencola.storage.addressbook

import io.opencola.model.Authority
import io.opencola.model.Id
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey

class PersonaAddressBookEntry(
    personaId: Id,
    entityId: Id,
    name: String,
    publicKey: PublicKey,
    address: URI,
    imageUri: URI?,
    isActive: Boolean,
    val keyPair: KeyPair // Maybe make private key - otherwise public key is redundant?
) : AddressBookEntry(personaId, entityId, name, publicKey, address, imageUri, isActive) {
    constructor(authority: Authority, keyPair: KeyPair) : this(
        authority.authorityId,
        authority.entityId,
        authority.name!!,
        authority.publicKey!!,
        authority.uri!!,
        authority.imageUri,
        authority.getActive(),
        keyPair
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val that = other as? PersonaAddressBookEntry ?: return false
        if(this.keyPair.public != that.keyPair.public) return false
        if(this.keyPair.private != that.keyPair.private) return false
        return super.equals(that)
    }
    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + keyPair.public.hashCode()
        result = 31 * result + keyPair.private.hashCode()
        return result
    }
}