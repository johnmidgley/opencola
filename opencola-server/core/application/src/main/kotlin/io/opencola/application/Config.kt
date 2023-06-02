package io.opencola.application

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import io.opencola.event.EventBusConfig
import io.opencola.network.NetworkConfig
import io.opencola.storage.addressbook.AddressBookConfig
import java.nio.file.Path

data class ResumeConfig(val enabled: Boolean = true, val desiredDelayMillis: Long = 10000, val maxDelayMillis: Long =30000)
data class SystemConfig(val resume: ResumeConfig = ResumeConfig())
data class SSLConfig(val port: Int = 5796, val sans: List<String> = emptyList())
data class ServerConfig(val host: String, val port: Int, val ssl: SSLConfig? = SSLConfig())
data class SolrConfig(val baseUrl: String, val connectionTimeoutMillis: Int, val socketTimeoutMillis: Int)
data class SearchConfig(val solr: SolrConfig?)
data class LoginConfig(val username: String = "opencola", val password: String? = null, val authenticationRequired: Boolean = true)
data class SecurityConfig(val login: LoginConfig = LoginConfig())
data class Resources(val allowEdit: Boolean = false)

data class Config(
    val name: String,
    val system: SystemConfig = SystemConfig(),
    val addressBook: AddressBookConfig = AddressBookConfig(),
    val eventBus: EventBusConfig = EventBusConfig(),
    val server: ServerConfig,
    val security: SecurityConfig,
    val search: SearchConfig?,
    val network: NetworkConfig = NetworkConfig(),
    val resources: Resources = Resources(),
)

// TODO: Use config layers instead of having to copy parts of config tree
// Use set() pattern (see AddressBookEntry) instead of creating these specific functions
fun Config.setName(name: String): Config {
    return Config(name, system, addressBook, eventBus, server, security, search, network)
}

fun Config.setServer(server: ServerConfig): Config {
    return Config(name, system, addressBook, eventBus, server, security, search, network)
}

fun Config.setNetwork(network: NetworkConfig): Config {
    return Config(name, system, addressBook, eventBus, server, security, search, network)
}

fun loadConfig(configPath: Path): Config {
    return ConfigLoaderBuilder.default()
        .addEnvironmentSource()
        .addFileSource(configPath.toFile())
        .build()
        .loadConfigOrThrow()
}


