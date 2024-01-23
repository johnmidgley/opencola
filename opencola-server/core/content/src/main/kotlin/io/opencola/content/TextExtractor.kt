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

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.BodyContentHandler
import java.io.ByteArrayInputStream
import java.io.InputStream


// https://hub.docker.com/r/apache/tika
// https://cwiki.apache.org/confluence/display/TIKA/TikaServer#TikaServer-Buildingfromsource
// Think about using solr to do Tika analysis / parsing as it should be able to do this

// TODO: Make interface and TikaContentExtractor implementation
// TODO: Try Boilerpipe: https://github.com/kohlschutter/boilerpipe
class TextExtractor {
    private fun getBody(stream: InputStream) : String {
        val handler = BodyContentHandler(-1) // -1 removes default text limit
        val metadata = Metadata()
        val parser = AutoDetectParser()
        val context = ParseContext()

        parser.parse(stream, handler, metadata, context)

        // TODO: Metadata may not include title. Fall back to normalized name from path
        return handler.toString()
    }

    fun getBody(bytes: ByteArray) : String {
        ByteArrayInputStream(bytes).use { return getBody(it) }
    }
}

