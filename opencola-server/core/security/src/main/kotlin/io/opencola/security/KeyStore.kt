package io.opencola.security

import java.security.KeyPair

interface KeyStore : PublicKeyProvider<String> {
    fun addKeyPair(alias: String, keyPair: KeyPair)
    fun getKeyPair(alias: String) : KeyPair?
    fun deleteKeyPair(alias: String)
    fun getAliases(): List<String>
    fun changePassword(newPassword: String)
}