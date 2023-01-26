package io.opencola.security

import java.security.PublicKey

interface PublicKeyProvider<T> {
    fun getPublicKey(alias: T) : PublicKey?
}