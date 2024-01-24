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
        if (this.keyPair.public != that.keyPair.public) return false
        if (this.keyPair.private != that.keyPair.private) return false
        return super.equals(that)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + keyPair.public.hashCode()
        result = 31 * result + keyPair.private.hashCode()
        return result
    }

    fun copy(
        personaId: Id = this.personaId,
        entityId: Id? = this.entityId,
        name: String? = this.name,
        publicKey: PublicKey? = this.publicKey,
        address: URI? = this.address,
        imageUri: URI? = this.imageUri,
        isActive: Boolean = this.isActive,
        keyPair: KeyPair = this.keyPair
    ): PersonaAddressBookEntry {
        return PersonaAddressBookEntry(
            personaId,
            entityId ?: this.entityId,
            name ?: this.name,
            publicKey ?: this.publicKey,
            address ?: this.address,
            imageUri ?: this.imageUri,
            isActive,
            keyPair
        )
    }
}