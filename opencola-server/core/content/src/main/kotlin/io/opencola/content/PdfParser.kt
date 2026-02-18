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

package io.opencola.content

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import javax.imageio.ImageIO

class OcPdfContentParser(textExtractor: TextExtractor, pdfBytes: ByteArray, uri: URI?) : PdfContentParser(pdfBytes, uri) {
    private val maxDescriptionLength = 300
    val text: String by lazy { textExtractor.getBody(pdfBytes) }

    override fun parseTitle(): String? {
        return uri?.toString()
    }

    override fun parseDescription(): String? {
        if (text.isBlank()) return null

        return text.substring(0,maxDescriptionLength).let {
            if (it.length < text.length) "$it..." else it
        }
    }

    // TODO: Image extraction

    override fun parseText(): String? {
        return text.let { if (it.isBlank()) null else it }
    }
}

fun getImages(document: PDDocument): Sequence<BufferedImage> {
    return document.pages
        .asSequence()
        .flatMap { page ->
        val resources = page.resources
        page.resources.xObjectNames
            .asSequence()
            .filter { resources.isImageXObject(it) }
            .mapNotNull { resources.getXObject(it) as? PDImageXObject }
            .map { it.image }
    }
}

fun getFirstImageFromPDF(pdfBytes: ByteArray): BufferedImage? {
    PDDocument.load(pdfBytes).use { return getImages(it).firstOrNull() }
}

fun renderFirstPageOfPDF(pdfBytes: ByteArray, scale: Float = 1.0f): BufferedImage {
    PDDocument.load(pdfBytes).use { document ->
        val renderer = PDFRenderer(document)
        return renderer.renderImage(0, scale)
    }
}

fun BufferedImage.toBytes(format: String) : ByteArray {
    ByteArrayOutputStream().use {  outStream ->
        outStream.use {
            ImageIO.write(this, format, it)
            return it.toByteArray()
        }
    }
}
