package io.opencola.storage

import io.opencola.model.Id
import io.opencola.security.KeyStore
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