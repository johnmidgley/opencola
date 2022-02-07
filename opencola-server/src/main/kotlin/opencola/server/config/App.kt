package opencola.server.config

import opencola.core.content.TextExtractor
import opencola.core.extensions.hexStringToByteArray
import opencola.core.model.Authority
import opencola.core.search.SearchService
import opencola.core.security.KeyStore
import opencola.core.security.Signator
import opencola.core.security.privateKeyFromBytes
import opencola.core.security.publicKeyFromBytes
import opencola.core.storage.LocalFileStore
import opencola.core.storage.SimpleEntityStore
import java.nio.file.Path
import java.security.KeyPair
import kotlin.io.path.Path

object App {
    // TODO: This should be handled by configuration / injection
    val authorityPublicKey = publicKeyFromBytes("3059301306072a8648ce3d020106082a8648ce3d03010703420004484a53f3dc6cecead248e0f299df8e191157010ac0892ef15a4158b8babd32eca522dc1c307578e5f0f76917c43795b775c4feba2f6007940a40f52efef5ffba".hexStringToByteArray())
    val authorityPrivateKey = privateKeyFromBytes("3041020100301306072a8648ce3d020106082a8648ce3d0301070427302502010104204158f0d52ed288ae60a84f8dc250b77d0c7263b336fd403b084618269285b172".hexStringToByteArray())
    val keyPair = KeyPair(authorityPublicKey, authorityPrivateKey)
    val authority = Authority(keyPair.public, name = "Authority")
    val storagePath = Path("/Users/johnmidgley/dev/opencola/storage")
    val entityStorePath: Path = storagePath.resolve("transactions.bin")
    val keyStorePath: Path = storagePath.resolve("keystore.pks")
    val keyStorePassword = "password"
    val keyStore = KeyStore(keyStorePath, keyStorePassword)
    val signator = Signator(keyStore)
    val entityStore = SimpleEntityStore(authority, signator, entityStorePath)
    val fileStorePath: Path = storagePath.resolve("filestore/")
    val fileStore = LocalFileStore(fileStorePath)
    val searchService = SearchService(authority)
    val textExtractor = TextExtractor()
}