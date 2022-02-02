package opencola.core.config

import java.nio.file.Path

// TODO: Use https://github.com/Kotlin/kotlinx.collections.immutable
// TODO: Add config layers, that allow for override. Use push / pop

class Config() {
    class Storage(){
        val storagePath = App.path.resolve("../storage")
        val entityStorePath: Path = storagePath.resolve("transactions.bin")
        val fileStorePath: Path = storagePath.resolve("filestore/")
    }

    val model = Model()
    class Model() {
        // TODO - This should really depend on the length of the hash
        val idLengthInBytes = 32 // 32 bytes for a sha256 hash
    }
}