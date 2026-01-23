/*
 * Copyright 2024-2026 OpenCola
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

import io.opencola.model.Id
import io.opencola.security.keystore.KeyStore
import io.opencola.security.PublicKeyProvider
import mu.KotlinLogging
import java.lang.StringBuilder
import java.security.PublicKey
import java.util.concurrent.CopyOnWriteArrayList

abstract class AbstractAddressBook : AddressBook {
    class KeyStorePublicKeyProvider(private val keyStore: KeyStore) : PublicKeyProvider<Id> {
        override fun getPublicKey(alias: Id): PublicKey? {
            return keyStore.getPublicKey(alias.toString())
        }
    }
    protected val logger = KotlinLogging.logger("AddressBook")
    protected val activeTag = "active"
    private val updateHandlers = CopyOnWriteArrayList<(AddressBookEntry?, AddressBookEntry?) -> Unit>()

    override fun toString(): String {
        val stringBuilder = StringBuilder("AddressBook\n")
        val (personas, peers) = getEntries().partition { it is PersonaAddressBookEntry }

        personas.forEach { stringBuilder.appendLine("\tPersona: $it") }
        peers.forEach { stringBuilder.appendLine("\tPeer: $it") }
        stringBuilder.appendLine("}")

        return stringBuilder.toString()
    }

    override fun addUpdateHandler(handler: (AddressBookEntry?, AddressBookEntry?) -> Unit){
        updateHandlers.add(handler)
    }

    override fun removeUpdateHandler(handler: (AddressBookEntry?, AddressBookEntry?) -> Unit){
        updateHandlers.remove(handler)
    }

    protected fun callUpdateHandlers(previousEntry: AddressBookEntry?,
                                     currentEntry: AddressBookEntry?,
                                     suppressUpdateHandler: ((AddressBookEntry?, AddressBookEntry?) -> Unit)? = null) {
        logger.info { "Address book update: $previousEntry -> $currentEntry" }
        updateHandlers.forEach {
            try {
                if(it != suppressUpdateHandler)
                    it(previousEntry, currentEntry)
            } catch (e: Exception) {
                logger.error { "Error calling update handler: $e" }
            }
        }
    }

    override fun getPublicKey(alias: Id): PublicKey? {
        return getEntries().firstOrNull { it.entityId == alias }?.publicKey
    }
}