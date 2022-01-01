package opencola.server

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import opencola.core.content.TextExtractor
import opencola.core.extensions.hexStringToByteArray
import opencola.core.model.Authority
import opencola.core.search.SearchService
import opencola.core.security.privateKeyFromBytes
import opencola.core.security.publicKeyFromBytes
import opencola.core.storage.EntityStore
import opencola.core.storage.FileStore
import opencola.core.storage.LocalFileStore
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting
import java.nio.file.Path
import java.security.KeyPair
import kotlin.io.path.Path

// TODO: This should be handled by configuration / injection
val authorityPublicKey = publicKeyFromBytes("3059301306072a8648ce3d020106082a8648ce3d03010703420004484a53f3dc6cecead248e0f299df8e191157010ac0892ef15a4158b8babd32eca522dc1c307578e5f0f76917c43795b775c4feba2f6007940a40f52efef5ffba".hexStringToByteArray())
val authorityPrivateKey = privateKeyFromBytes("3041020100301306072a8648ce3d020106082a8648ce3d0301070427302502010104204158f0d52ed288ae60a84f8dc250b77d0c7263b336fd403b084618269285b172".hexStringToByteArray())
val keyPair = KeyPair(authorityPublicKey, authorityPrivateKey)
val authority = Authority(keyPair, name = "Authority")
val entityStore = EntityStore(listOf(authority).toSet())
val storagePath = Path("/Users/johnmidgley/dev/opencola/storage")
val entityStorePath: Path = storagePath.resolve("transactions.json")
val fileStorePath: Path = storagePath.resolve("filestore/")
val fileStore = LocalFileStore(fileStorePath)
val searchService = SearchService(authority)
val textExtractor = TextExtractor()

fun main() {
    entityStore.load(entityStorePath)

    embeddedServer(Netty, port = 5795, host = "0.0.0.0") {
        configureHTTP()
        configureContentNegotiation()
        configureRouting()
    }.start(wait = true)
}
