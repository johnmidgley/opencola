package io.opencola.storage

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
    val keyPair: KeyPair
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
}