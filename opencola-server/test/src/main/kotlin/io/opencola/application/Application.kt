package io.opencola.application

import io.opencola.storage.addressbook.AddressBook
import io.opencola.model.Id
import io.opencola.security.keystore.defaultPasswordHash
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectory

private var serverNum = 0

// TODO: Replace this with method use for testing NetworkNode
fun getApplications(
    rootStoragePath: Path,
    baseAppConfig: Config,
    basePortNumber: Int,
    nServers: Int,
    personaAddress: URI? = null // When null, http is used. Used to specify relay address, when needed for test
): List<Application> {
    @Suppress("HttpUrlsUsage") val instanceConfigs =
        (0 until nServers).map {
            object {
                val name = "Server-${serverNum++}"
                val storagePath = rootStoragePath.resolve(name).createDirectory()
                val config =
                    baseAppConfig
                        .setName(name)
                        .setServer(ServerConfig(baseAppConfig.server.host, basePortNumber + serverNum, null))
                val keyPairs = Application.getOrCreateRootKeyPair(storagePath, defaultPasswordHash)
                val address = personaAddress ?: URI("http://${config.server.host}:${config.server.port}")
            }
        }

    val applications = instanceConfigs.map { ic ->
        Application.instance(ic.storagePath, ic.config, ic.keyPairs,defaultPasswordHash).also { application ->
            // Connect to peers
            val addressBook = application.inject<AddressBook>()
            val persona = addressBook.getEntries().filterIsInstance<PersonaAddressBookEntry>().single()
            personaAddress?.let { addressBook.updateEntry(persona.copy(address = it)) }

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


