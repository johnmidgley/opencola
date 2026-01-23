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