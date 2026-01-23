/*
 * Copyright 2024-2026 OpenCola
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

package io.opencola.relay.server

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType

class CommandLineArgs(args: Array<String>) {
    private val parser = ArgParser("relay")
    val storage by parser.option(ArgType.String, shortName = "s", description = "Storage path")

    init {
        // https://github.com/Kotlin/kotlinx-cli
        parser.parse(args)
    }
}