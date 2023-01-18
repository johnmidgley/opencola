package opencola.core.config

import io.opencola.core.config.*
import io.opencola.storage.AddressBook
import io.opencola.model.Authority
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectory

private var serverNum = 0

// TODO: Replace this with method use for testing NetworkNode
fun getApplications(
    rootStoragePath: Path,
    baseAppConfig: Config,
    basePortNumber: Int,
    nServers: Int
): List<Application> {
    val instanceConfigs =
        (0 until nServers).map { i ->
            object {
                val name = "Server-${serverNum++}"
                val storagePath = rootStoragePath.resolve(name).createDirectory()
                val config =
                    baseAppConfig
                        .setName(name)
                        .setServer(ServerConfig(baseAppConfig.server.host, basePortNumber + i, null))
                val keyPair = Application.getOrCreateRootKeyPair(storagePath, "password")
                val address = URI("http://${config.server.host}:${config.server.port}")
            }
        }

    val applications = instanceConfigs.map { ic ->
        Application.instance(ic.storagePath, ic.config, ic.keyPair,"password").also { application ->
            // Connect to peers
            val authorityId = application.inject<Authority>().authorityId
            val addressBook = application.inject<AddressBook>()
            instanceConfigs
                .filter { it != ic }
                .forEach {
                    val peer = Authority(authorityId, it.keyPair.public, it.address, it.name, tags = setOf("active"))
                    addressBook.updateAuthority(peer)
                }
        }
    }

    return applications
}


