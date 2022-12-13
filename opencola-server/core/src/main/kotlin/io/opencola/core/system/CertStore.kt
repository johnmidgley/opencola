package io.opencola.core.system

import java.nio.file.Path
import kotlin.io.path.exists

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

fun installCertMac(storagePath: Path) {
    val certPath = getCertStorePath(storagePath)
    runCommand("security add-trusted-cert -k login.keychain-db".split(" ").plus(certPath.toString()))
    setCertInstalled(storagePath)
}