package io.opencola.core.config

import com.sksamuel.hoplite.ConfigLoader
import java.net.URI
import java.nio.file.Path

// TODO: Add config layers, that allow for override. Use push / pop
// https://github.com/sksamuel/hoplite
data class EventBusConfig(val name: String = "event-bus", val maxAttempts: Int = 3)
data class SSLConfig(val port: Int = 5796, val sans: List<String> = emptyList())
data class ServerConfig(val host: String, val port: Int, val ssl: SSLConfig? = SSLConfig())
data class LoginConfig(val username: String = "opencola", val password: String? = null, val authenticationRequired: Boolean = true)
data class SecurityConfig(val login: LoginConfig = LoginConfig())
data class SolrConfig(val baseUrl: String, val connectionTimeoutMillis: Int, val socketTimeoutMillis: Int)
data class SearchConfig(val solr: SolrConfig?)
data class PeerConfig(val id: String, val publicKey: String, val name: String, val host: String, val active: Boolean = true)
data class SocksProxy(val host: String, val port: Int)
data class NetworkConfig(val peers: List<PeerConfig> = emptyList(),
                         val defaultAddress: URI = URI("ocr://relay.opencola.net"),
                         val requestTimeoutMilliseconds: Long = 20000,
                         val socksProxy: SocksProxy? = null)
data class Resources(val allowEdit: Boolean = false)


data class Config(
    val name: String,
    val eventBus: EventBusConfig = EventBusConfig(),
    val server: ServerConfig,
    val security: SecurityConfig,
    val search: SearchConfig?,
    val network: NetworkConfig = NetworkConfig(),
    val resources: Resources = Resources(),
)

// TODO: Use config layers instead of having to copy parts of config tree
fun Config.setName(name: String): Config {
    return Config(name, eventBus, server, security, search, network)
}

fun Config.setServer(server: ServerConfig): Config {
    return Config(name, eventBus, server, security, search, network)
}

fun Config.setNetwork(network: NetworkConfig): Config {
    return Config(name, eventBus, server, security, search, network)
}

fun loadConfig(configPath: Path): Config {
    return ConfigLoader().loadConfigOrThrow(configPath)
}


