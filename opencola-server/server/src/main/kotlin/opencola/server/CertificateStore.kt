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

import io.opencola.application.SSLConfig
import io.opencola.security.certificate.convertCertificateToPEM
import io.opencola.security.certificate.generateRSAKeyPair
import io.opencola.security.certificate.generateSelfSignedV3Certificate
import mu.KotlinLogging
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.file.Path
import java.security.KeyStore
import kotlin.io.path.isDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger("opencola")

fun getSanEntriesFromNetworkInterfaces(): List<String> {
    return NetworkInterface.getNetworkInterfaces()
        .toList()
        .flatMap { networkInterface ->
            networkInterface.inetAddresses
                .toList()
                .filterIsInstance<Inet4Address>()
                .map { "ip:${it.hostAddress}" }
        }.toList()
        .plus("dns:localhost")
}

fun getSanEntries(sslConfig: SSLConfig): List<String> {
    // If subject alternative names are specified in config, as is the case when running in docker, use them,
    // otherwise generate from network interfaces
    return sslConfig
        .sans
        .ifEmpty { getSanEntriesFromNetworkInterfaces() }
        .also { logger.info { "Subject Alternative Names: $it" } }
}

fun createSSLCertificateAndStore(storagePath: Path, password: String, sslConfig: SSLConfig) {
    logger.info { "Creating certificate store" }
    val certPath = storagePath.resolve("cert")
    val sans = getSanEntries(sslConfig)
    val keyPair = generateRSAKeyPair()
    val cert = generateSelfSignedV3Certificate("CN=opencola, O=OpenCola", sans, keyPair)
    val keystore = KeyStore.getInstance("PKCS12","BC")
    keystore.load(null, password.toCharArray())
    keystore.setKeyEntry("opencola-ssl", keyPair.private, null, arrayOf(cert))

    // Write keystore for SSL use
    val keyStoreFile = storagePath.resolve("cert/opencola-ssl.pks")
    keyStoreFile.outputStream().use {
        keystore.store(it, password.toCharArray())
    }

    // Write certs to be used by devices
    certPath.resolve("opencola-ssl.pem").writeText(convertCertificateToPEM(cert))
    certPath.resolve("opencola-ssl.der").writeBytes(cert.encoded)

    logger.info { "Create cert with SANS: ${sans.joinToString(", " )}" }
}

fun getSSLCertificateStore(storagePath: Path, password: String, sslConfig: SSLConfig): KeyStore {
    val certStoragePath = storagePath.resolve("cert")
    if(!certStoragePath.isDirectory()) {
        throw IllegalStateException("'cert' directory doesn't exist in $certStoragePath. Please copy from install storage directory")
    }

    val keyStoreFile = certStoragePath.resolve("opencola-ssl.pks").toFile()
    if(!keyStoreFile.exists()) {
        logger.info { "SSL Certificate store not found" }
        createSSLCertificateAndStore(storagePath, password, sslConfig)
        // openFile(certStoragePath.resolve("opencola-ssl.der"))
    }

    val keystore = KeyStore.getInstance("PKCS12","BC")
    keyStoreFile.inputStream().use { keystore.load(keyStoreFile.inputStream(), password.toCharArray()) }

    return keystore
}