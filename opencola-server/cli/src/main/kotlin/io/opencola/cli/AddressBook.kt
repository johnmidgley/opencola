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

package io.opencola.cli

import io.opencola.storage.addressbook.AddressBook

fun checkAddressBook(addressBook: AddressBook) {
    val personas = addressBook.getPersonas()
    val personaIds = personas.map { it.personaId }
    val peers = addressBook.getPeers()

    for(persona in personas) {
        if(persona.personaId != persona.entityId){
            println("ERROR: Found ${persona.name}'s personaId (${persona.personaId}) does not match entityId (${persona.entityId})")
        }
    }

    for(peer in peers) {
        if(!personaIds.contains(peer.personaId)){
            println("ERROR: Peer ${peer.name}:${peer.entityId} does not have valid persona: ${peer.personaId}")
        }
    }
}