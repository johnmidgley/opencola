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

package io.opencola.cli

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource

data class CredentialsConfig(val password: String? = null)

data class OcConfig(
    val credentials: CredentialsConfig,
)

data class Config(
    val oc: OcConfig
)

fun loadConfig(): Config {
    return ConfigLoaderBuilder.default()
        .addEnvironmentSource()
        .build()
        .loadConfigOrThrow()
}