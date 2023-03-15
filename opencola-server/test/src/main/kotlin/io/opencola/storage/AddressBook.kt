package io.opencola.storage

import io.opencola.model.Id
import io.opencola.security.generateKeyPair
import java.net.URI
import java.security.PublicKey

fun createPersona(name: String, isActive: Boolean = true): PersonaAddressBookEntry {
    val keyPair = generateKeyPair()
    val personaId = Id.ofPublicKey(keyPair.public)
    return PersonaAddressBookEntry(
        personaId,
        personaId,
        name,
        keyPair.public,
        URI("mock://$personaId"),
        null,
        isActive,
        keyPair
    )
}

fun createPeer(
    personaId: Id,
    name: String,
    isActive: Boolean = true,
    publicKey: PublicKey = generateKeyPair().public
): AddressBookEntry {
    val entityId = Id.ofPublicKey(publicKey)
    return AddressBookEntry(
        personaId,
        entityId,
        name,
        publicKey,
        URI("mock://$entityId"),
        null,
        isActive,
    )
}