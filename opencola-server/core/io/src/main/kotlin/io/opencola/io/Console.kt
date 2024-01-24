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

package io.opencola.io

const val escape = "\u001B"
const val red = "$escape[31m"
const val yellow = "$escape[33m"
const val reset = "$escape[0m"

enum class Color(val code: String) {
    RED("$escape[31m"),
    YELLOW("$escape[33m"),
}

fun colorize(color: Color, s: String): String {
    return "${color.code}$s$reset"
}