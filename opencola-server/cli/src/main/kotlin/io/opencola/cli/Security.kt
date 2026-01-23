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

package io.opencola.cli

import io.opencola.security.hash.Hash
import io.opencola.security.hash.Sha256Hash


fun readPassword(): String {
    val console = System.console()

    return if (console != null) {
        val password = console.readPassword()
        String(password)
    } else {
        readln()
    }
}

fun getPasswordHash(config: Config): Hash {
    val password = config.oc.credentials.password

    return Sha256Hash.ofString(
        if (password != null)
            password
        else {
            println("You can set the OCR_PASSWORD environment variable to avoid having to enter your password.")
            print("Password: ")
            readPassword()
        }
    )
}