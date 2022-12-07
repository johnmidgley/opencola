package opencola.server

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default

class CommandLineArgs(args: Array<String>) {
    // TODO: App parameter is now ignored. Was only needed to locate resources, which are now bundled directly.
    //  Leaving here until no scripts depend on it
    @Suppress("UNUSED_VARIABLE")
    val parser = ArgParser("oc")
    val app by parser.option(ArgType.String, shortName = "a", description = "Application path").default(".")
    val storage by parser.option(ArgType.String, shortName = "s", description = "Storage path").default("")
    val desktop by parser.option(ArgType.Boolean, shortName = "d", description = "Desktop mode").default(false)

    init {
        // https://github.com/Kotlin/kotlinx-cli
        parser.parse(args)
    }
}
