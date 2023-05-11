package io.opencola.storage

import io.opencola.io.StdoutMonitor
import io.opencola.model.Id
import io.opencola.security.generateKeyPair
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
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

fun AddressBook.addPersona(name: String, isActive: Boolean = true): PersonaAddressBookEntry {
    return createPersona(name, isActive).also { updateEntry(it) }
}

fun AddressBook.addPeer(personaId: Id,
                        name: String,
                        isActive: Boolean = true,
                        publicKey: PublicKey = generateKeyPair().public
): AddressBookEntry {
    return createPeer(personaId, name, isActive, publicKey).also { updateEntry(it) }
}

fun StdoutMonitor.waitForAddressBookAdd(personaId: Id, peerId: Id) {
    waitUntil {it.contains("Address book update: null -> AddressBookEntry(personaId=$personaId, entityId=$peerId") }
}


