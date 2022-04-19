package opencola.core.config

import opencola.core.extensions.toHexString
import opencola.core.model.Id

private var serverNum = 0;

fun getApplications(nServers: Int, baseConfig: Config, basePortNumber: Int): List<Application> {
    val configKeyPairs = (0 until nServers)
        .map { baseConfig.setName("Server-${serverNum++}") }
        .mapIndexed { i, it -> it.setServer(ServerConfig(it.server.host, basePortNumber + i)) }
        .map { it.setStoragePath(it.storage.path.resolve(it.name)) }
        .map { Pair(it, Application.getOrCreateRootPublicKey(it.storage.path, baseConfig.security)) }

    return configKeyPairs.mapIndexed { i, configKeyPair ->
        val peerConfigs = (0 until nServers)
            .filter { it != i }
            .map {
                val (config, key) = configKeyPairs[it]
                PeerConfig(
                    Id.ofPublicKey(key).toString(),
                    key.encoded.toHexString(),
                    config.name,
                    "${config.server.host}:${config.server.port}"
                )
            }

        Application.instance(configKeyPair.first.setNetwork(NetworkConfig(peerConfigs)), configKeyPair.second)
    }
}