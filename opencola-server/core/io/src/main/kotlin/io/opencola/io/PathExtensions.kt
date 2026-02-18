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

package io.opencola.io

import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory

fun Path.recursiveDelete() {
    if (this.isDirectory()) {
        this.toFile().listFiles()?.forEach {
            if (it.isDirectory) {
                it.toPath().recursiveDelete()
            }

            it.delete()
        }
    } else
        this.deleteIfExists()
}