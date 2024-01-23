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

package io.opencola.system

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

// TODO: Probably not right place for this

fun getCertStorePath(storagePath: Path) : Path {
    return storagePath.resolve("cert/opencola-ssl.pem")
}

private fun certInstalledIndicatorPath(storagePath: Path): Path {
    return getCertStorePath(storagePath)
        .toString()
        .substringBeforeLast(".")
        .plus(".installed")
        .let { Path.of(it) }
}
fun isCertInstalled(storagePath: Path): Boolean {
    return certInstalledIndicatorPath(storagePath).exists()
}

fun setCertInstalled(storagePath: Path) {
    certInstalledIndicatorPath(storagePath).toFile().createNewFile()
}

fun installTrustedCACertMac(storagePath: Path) {
    val certPath = getCertStorePath(storagePath)
    runCommand("security add-trusted-cert -k login.keychain-db".split(" ").plus(certPath.toString()))
    setCertInstalled(storagePath)
}

fun installTrustedCACertWindows(storagePath: Path) {
    val certPath = getCertStorePath(storagePath)
    val command = """
        certutil -addstore -user root "$certPath"
    """.trimIndent()
    val installCertBat = kotlin.io.path.createTempFile(suffix = "installCert.bat").also { it.writeText(command) }
    runCommand(listOf(installCertBat.toString())).joinToString("\n")
    setCertInstalled(storagePath)
}
fun installTrustedCACert(storagePath: Path) {
    when (val os = getOS()) {
        OS.Mac -> installTrustedCACertMac(storagePath)
        OS.Windows -> installTrustedCACertWindows(storagePath)
        else -> throw IllegalArgumentException("Don't know how to add trusted CA cert for os: $os")
    }

}