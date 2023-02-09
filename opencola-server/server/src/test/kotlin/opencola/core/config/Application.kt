package opencola.core.config

import io.opencola.application.*
import io.opencola.storage.AddressBook
import io.opencola.model.Id
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.PersonaAddressBookEntry
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
                val keyPairs = Application.getOrCreateRootKeyPair(storagePath, "password")
                val address = URI("http://${config.server.host}:${config.server.port}")
            }
        }

    val applications = instanceConfigs.map { ic ->
        Application.instance(ic.storagePath, ic.config, ic.keyPairs,"password").also { application ->
            // Connect to peers
            val addressBook = application.inject<AddressBook>()
            val persona = addressBook.getEntries().filterIsInstance<PersonaAddressBookEntry>().single()
            instanceConfigs
                .filter { it != ic }
                .forEach {
                    val keyPair = it.keyPairs.single()
                    val peer = AddressBookEntry(
                        persona.personaId,
                        Id.ofPublicKey(keyPair.public),
                        it.name,
                        keyPair.public,
                        it.address,
                        null,
                        true
                    )
                    addressBook.updateEntry(peer)
                }
        }
    }

    return applications
}


