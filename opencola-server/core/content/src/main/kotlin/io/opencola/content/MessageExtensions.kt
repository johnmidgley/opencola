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

package io.opencola.content

import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.DefaultMessageWriter
import java.io.ByteArrayOutputStream

fun Message.bytes(): ByteArray {
    return  ByteArrayOutputStream().use {
        DefaultMessageWriter().writeMessage(this, it)
        it.toByteArray()
    }
}