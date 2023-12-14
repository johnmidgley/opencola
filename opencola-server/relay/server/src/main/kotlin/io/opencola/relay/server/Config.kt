package io.opencola.relay.server

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import io.opencola.model.Id
import io.opencola.relay.common.defaultOCRPort
import io.opencola.security.privateKeyFromBytes
import io.opencola.security.publicKeyFromBytes
import io.opencola.util.Base58
import io.opencola.util.toBase58
import java.nio.file.Path
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import kotlin.io.path.Path

data class ServerConfig(
    val port: Int = defaultOCRPort,
    val callLogging: Boolean = false
)

data class CapacityConfig(
    val maxConnections: Long = 10000,
    val maxBytesStored: Long = 1024 * 1024 * 1024 * 10L,
    val maxPayloadSize: Long = 1024 * 1024 * 50L,
)

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
        require(publicKeyBase58.isNotEmpty()) { "publicKeyBase58 must not be empty" }
        publicKeyFromBytes(Base58.decode(publicKeyBase58))
    }

    val privateKey: PrivateKey by lazy {
        require(privateKeyBase58.isNotEmpty()) { "privateKeyBase58 must not be empty" }
        privateKeyFromBytes(Base58.decode(privateKeyBase58))
    }

    val keyPair: KeyPair by lazy {
        KeyPair(publicKey, privateKey)
    }

    val rootId: Id by lazy {
        require(rootIdBase58.isNotEmpty()) { "rootIdBase58 must not be empty" }
        Id.decode(rootIdBase58)
    }
}

data class RelayConfig(
    val storagePath: Path = Path("storage"),
    val server: ServerConfig = ServerConfig(),
    val capacity: CapacityConfig = CapacityConfig(),
    val security: SecurityConfig,
)

data class Config(val relay: RelayConfig)

fun loadConfig(configPath: Path): Config {
    return ConfigLoaderBuilder.default()
        .addEnvironmentSource()
        .addFileSource(configPath.toFile())
        .build()
        .loadConfigOrThrow()
}