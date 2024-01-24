/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
