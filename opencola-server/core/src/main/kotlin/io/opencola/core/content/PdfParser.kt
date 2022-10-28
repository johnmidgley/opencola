package io.opencola.core.content

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

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
