package opencola.core.config

import opencola.core.extensions.toHexString
import opencola.core.model.Id
import java.nio.file.Path
import kotlin.io.path.createDirectory

private var serverNum = 0

fun getApplications(
    applicationPath: Path,
    storagePath: Path,
    baseConfig: Config,
    basePortNumber: Int,
    nServers: Int
): List<Application> {
    val configTuples = (0 until nServers)
        .map { baseConfig.setName("Server-${serverNum++}") }
        .mapIndexed { i, it -> it.setServer(ServerConfig(it.server.host, basePortNumber + i)) }
        .map { config ->
            val instanceStoragePath = storagePath.resolve(config.name).createDirectory()
            Triple(
                instanceStoragePath,
                config,
                Application.getOrCreateRootPublicKey(instanceStoragePath, baseConfig.security)
            )
        }

    return configTuples.mapIndexed { i, (storagePath, config, publicKey) ->
        val peerConfigs = (0 until nServers)
            .filter { it != i }
            .map {
                val (_, peerConfig, key) = configTuples[it]
                PeerConfig(
                    Id.ofPublicKey(key).toString(),
                    key.encoded.toHexString(),
                    peerConfig.name,
                    "${peerConfig.server.host}:${peerConfig.server.port}"
                )
            }

        Application.instance(
            applicationPath,
            storagePath,
            config.setNetwork(NetworkConfig(peerConfigs)),
            publicKey
        )
    }
}
