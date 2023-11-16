package io.opencola.relay.server

import io.opencola.model.Id
import io.opencola.security.privateKeyFromBytes
import io.opencola.security.publicKeyFromBytes
import io.opencola.util.Base58
import io.opencola.util.toBase58
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

data class SecurityConfig(
    val publicKeyBase58: String,
    val privateKeyBase58: String,
    val rootPublicKeyBase58: String,
    val rooPrivateKeyBase58: String,
    val numChallengeBytes: Int = 32
) {
    constructor(keyPair: KeyPair, rootKeyPair: KeyPair) : this(
        keyPair.public.encoded.toBase58(),
        keyPair.private.encoded.toBase58(),
        rootKeyPair.public.encoded.toBase58(),
        rootKeyPair.private.encoded.toBase58()
    )

    override fun toString(): String {
        return "SecurityConfig(publicKeyBase58='$publicKeyBase58', rootId='$rootId', numChallengeBytes=$numChallengeBytes)"
    }

    val publicKey: PublicKey by lazy {
        publicKeyFromBytes(Base58.decode(publicKeyBase58))
    }

    val privateKey: PrivateKey by lazy {
        privateKeyFromBytes(Base58.decode(privateKeyBase58))
    }

    val keyPair: KeyPair by lazy {
        KeyPair(publicKey, privateKey)
    }

    private val rootPublicKey: PublicKey by lazy {
        publicKeyFromBytes(Base58.decode(rootPublicKeyBase58))
    }

    private val rootPrivateKey: PrivateKey by lazy {
        privateKeyFromBytes(Base58.decode(rooPrivateKeyBase58))
    }

    val rooKeyPair: KeyPair by lazy {
        KeyPair(rootPublicKey, rootPrivateKey)
    }

    val rootId: Id by lazy {
        Id.ofPublicKey(rootPublicKey)
    }
}

data class CapacityConfig(
    val maxConnections: Long = 10000,
    val maxBytesStored: Long = 1024 * 1024 * 1024 * 10L,
    val maxPayloadSize: Long = 1024 * 1024 * 50L,
)

data class Config(
    val capacity: CapacityConfig = CapacityConfig(),
    val security: SecurityConfig,
)