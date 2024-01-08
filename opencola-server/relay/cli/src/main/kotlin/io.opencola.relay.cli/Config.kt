package io.opencola.relay.cli

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

const val configFileName = "ocr-cli.yaml"

fun getResourceAsStream(name: String): InputStream? {
    return object {}.javaClass.classLoader.getResourceAsStream(name)
}

fun getDefaultConfigYaml() : String {
    val yamlStream = getResourceAsStream(configFileName)
    return yamlStream!!.bufferedReader().readText()
}

fun initConfig(storagePath: Path) {
    val yamlPath = storagePath.resolve(configFileName)
    if (!yamlPath.exists()) {
        println("Creating default config file: $yamlPath")
        yamlPath.writeText(getDefaultConfigYaml())
    }
}

data class CredentialsConfig(val password: String? = null)

data class ServerConfig(
    val uri: URI,
    val connectTimeoutMilliseconds: Long = 3000,
    val requestTimeoutMilliseconds: Long = 5000,
)

data class OcrConfig(
    val server: ServerConfig,
    val credentials: CredentialsConfig,
)

data class Config(
    val ocr: OcrConfig,
)

fun loadConfig(configPath: Path): Config {
    return ConfigLoaderBuilder.default()
        .addEnvironmentSource()
        .addFileSource(configPath.toFile())
        .build()
        .loadConfigOrThrow()
}