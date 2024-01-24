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

import java.net.URI

class HtmlContentParser(content: ByteArray, uri: URI?) : AbstractContentParser(content, uri) {
    val htmlParser = JsoupHtmlParser(String(content))

    override fun parseTitle(): String? {
        return htmlParser.parseTitle() ?: super.parseTitle()
    }

    override fun parseDescription(): String? {
        return htmlParser.parseDescription()
    }

    override fun parseImageUri(): URI? {
        return htmlParser.parseImageUri()
    }

    override fun parseText(): String? {
        return htmlParser.parseText()
    }

}