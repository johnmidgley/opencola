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