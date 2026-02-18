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

package io.opencola.application

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import io.opencola.event.bus.EventBusConfig
import io.opencola.network.NetworkConfig
import java.nio.file.Path

// Data classes tha represent configuration options for the application.

data class ResumeConfig(val enabled: Boolean = false, val desiredDelayMillis: Long = 10000, val maxDelayMillis: Long =30000)
data class SystemConfig(val resume: ResumeConfig = ResumeConfig())
data class SSLConfig(val port: Int = 5796, val sans: List<String> = emptyList())
data class ServerConfig(val host: String, val port: Int, val ssl: SSLConfig? = SSLConfig())
data class LoginConfig(val username: String = "opencola", val password: String? = null, val authenticationRequired: Boolean = true)
data class SecurityConfig(val login: LoginConfig = LoginConfig())
data class ResourcesConfig(val allowEdit: Boolean = false)

data class Config(
    val name: String,
    val system: SystemConfig = SystemConfig(),
    val eventBus: EventBusConfig = EventBusConfig(),
    val server: ServerConfig,
    val security: SecurityConfig,
    val network: NetworkConfig = NetworkConfig(),
    val resources: ResourcesConfig = ResourcesConfig(),
)

// TODO: Use config layers instead of having to copy parts of config tree
// Use set() pattern (see AddressBookEntry) instead of creating these specific functions
fun Config.setName(name: String): Config {
    return Config(name, system, eventBus, server, security, network)
}

fun Config.setServer(server: ServerConfig): Config {
    return Config(name, system, eventBus, server, security, network)
}

fun Config.setNetwork(network: NetworkConfig): Config {
    return Config(name, system, eventBus, server, security, network)
}

fun loadConfig(configPath: Path): Config {
    return ConfigLoaderBuilder.default()
        .addEnvironmentSource()
        .addFileSource(configPath.toFile())
        .build()
        .loadConfigOrThrow()
}


