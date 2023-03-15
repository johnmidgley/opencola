package io.opencola.security

import java.security.KeyPair
import java.security.PublicKey

class MockKeyStore : KeyStore {
    private val keyPairs = mutableMapOf<String, KeyPair>()

    override fun addKeyPair(alias: String, keyPair: KeyPair) {
        keyPairs[alias] = keyPair
    }

    override fun getKeyPair(alias: String): KeyPair? {
        return keyPairs[alias]
    }

    override fun deleteKeyPair(alias: String) {
        keyPairs.remove(alias)
    }

    override fun getAliases(): List<String> {
        return keyPairs.keys.toList()
    }

    override fun changePassword(newPassword: String) {
        return
    }

    override fun getPublicKey(alias: String): PublicKey? {
        return keyPairs[alias]?.public
    }
}