package opencola.core.config

import java.nio.file.Path
import kotlin.io.path.Path

object Config {
    val storagePath = Path(System.getProperty("user.dir"), "..", "storage")
    val entityStorePath: Path = storagePath.resolve("transactions.bin")
    val fileStorePath: Path = storagePath.resolve("filestore/")
}