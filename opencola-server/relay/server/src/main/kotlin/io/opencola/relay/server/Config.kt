package io.opencola.relay.server

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import io.opencola.model.Id
import io.opencola.security.privateKeyFromBytes
import io.opencola.security.publicKeyFromBytes
import io.opencola.util.Base58
import io.opencola.util.toBase58
import java.nio.file.Path
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

data class SecurityConfig(
    val publicKeyBase58: String,
    val privateKeyBase58: String,
    val rootIdBase58: String,
    val numChallengeBytes: Int = 32
) {
    constructor(keyPair: KeyPair, rootId: Id) : this(
        keyPair.public.encoded.toBase58(),
        keyPair.private.encoded.toBase58(),
        rootId.toString()
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

    val rootId: Id by lazy {
        Id.decode(rootIdBase58)
    }
}

data class CapacityConfig(
    val maxConnections: Long = 10000,
    val maxBytesStored: Long = 1024 * 1024 * 1024 * 10L,
    val maxPayloadSize: Long = 1024 * 1024 * 50L,
)

data class Config(
    val storagePath: Path,
    val capacity: CapacityConfig = CapacityConfig(),
    val security: SecurityConfig,
) {
    constructor(capacity: CapacityConfig = CapacityConfig(), security: SecurityConfig) : this(
        storagePath = Path.of("storage"),
        capacity = capacity,
        security = security
    )
}

fun loadConfig(configPath: Path): Config {
    return ConfigLoaderBuilder.default()
        .addEnvironmentSource()
        .addFileSource(configPath.toFile())
        .build()
        .loadConfigOrThrow()
}