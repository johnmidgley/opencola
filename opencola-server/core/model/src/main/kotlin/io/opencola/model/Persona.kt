package io.opencola.model

import java.net.URI
import java.security.KeyPair

class Persona : Authority {
    val keyPair: KeyPair

    constructor(keyPair: KeyPair, uri: URI, name: String) : super(keyPair.public, uri, name) {
        this.keyPair = keyPair
    }

    constructor(authority: Authority, keyPair: KeyPair) : super(authority.getAllFacts()) {
        this.keyPair = keyPair
    }
}