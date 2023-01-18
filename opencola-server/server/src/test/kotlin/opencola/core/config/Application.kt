package opencola.core.config

import io.opencola.core.config.*
import io.opencola.core.storage.AddressBook
import io.opencola.model.Authority
import io.opencola.security.decodePublicKey
import io.opencola.security.encode
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectory

private var serverNum = 0

// TODO: Replace this with method use for testing NetworkNode
fun getApplications(
    storagePath: Path,
    baseAppConfig: Config,
    basePortNumber: Int,
    nServers: Int
): List<Application> {
    val instanceConfigs = (0 until nServers)
        .map { baseAppConfig.setName("Server-${serverNum++}") }
        .mapIndexed { i, it -> it.setServer(ServerConfig(it.server.host, basePortNumber + i, null)) }
        .map { appConfig ->
            val instanceStoragePath = storagePath.resolve(appConfig.name).createDirectory()
            Triple(
                instanceStoragePath,
                appConfig,
                Application.getOrCreateRootKeyPair(instanceStoragePath, "password")
            )
        }

    return instanceConfigs.mapIndexed { i, (instanceStoragePath, config, publicKey) ->
        val peerConfigs = (0 until nServers)
            .filter { it != i }
            .map {
                val (_, peerConfig, keyPair) = instanceConfigs[it]
                object {
                    val publicKey = keyPair.public.encode()
                    val name = peerConfig.name
                    val address = URI("http://${peerConfig.server.host}:${peerConfig.server.port}")
                }
            }

        Application.instance(
            instanceStoragePath,
            config.setNetwork(NetworkConfig(requestTimeoutMilliseconds = config.network.requestTimeoutMilliseconds)),
            publicKey,
            "password"
        ).also { app ->
            val authorityId = app.inject<Authority>().authorityId
            peerConfigs.forEach {
                val tags = setOf("active")
                val peerAuthority = Authority(authorityId, decodePublicKey(it.publicKey), it.address, it.name, tags = tags)

                app.inject<AddressBook>().updateAuthority(peerAuthority)
            }
        }
    }
}


