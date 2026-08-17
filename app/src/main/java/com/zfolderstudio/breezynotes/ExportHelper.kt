package com.zfolderstudio.breezynotes

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExportHelper {

    suspend fun exportNote(
        context: Context,
        uri: Uri,
        title: String,
        content: String,
        format: String
    ) = withContext(Dispatchers.IO) {
        val outputStream = context.contentResolver.openOutputStream(uri) ?: throw Exception("Cannot open stream")
        outputStream.use { out ->
            when (format) {
                "txt", "md" -> {
                    out.write(title.toByteArray())
                    out.write("\n\n".toByteArray())
                    out.write(content.toByteArray())
                }
                "html" -> {
                    val htmlContent = """
                        <!DOCTYPE html>
                        <html>
                        <head><title>$title</title></head>
                        <body>
                        <h1>$title</h1>
                        <p>${content.replace("\n", "<br>")}</p>
                        </body>
                        </html>
                    """.trimIndent()
                    out.write(htmlContent.toByteArray())
                }
                "pdf" -> {
                    val document = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    
                    val textPaint = TextPaint().apply {
                        color = Color.BLACK
                        textSize = 14f
                    }
                    val titlePaint = TextPaint().apply {
                        color = Color.BLACK
                        textSize = 24f
                        isFakeBoldText = true
                    }
                    
                    val padding = 40
                    val maxWidth = pageInfo.pageWidth - (padding * 2)
                    
                    val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, maxWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build()
                        
                    val contentLayout = StaticLayout.Builder.obtain(content, 0, content.length, textPaint, maxWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build()

                    var currentPage = document.startPage(pageInfo)
                    var canvas = currentPage.canvas
                    var currentY = padding.toFloat()
                    
                    canvas.translate(padding.toFloat(), currentY)
                    titleLayout.draw(canvas)
                    canvas.translate(-padding.toFloat(), -currentY)
                    currentY += titleLayout.height + 20f
                    
                    val pageHeight = pageInfo.pageHeight - padding
                    
                    for (i in 0 until contentLayout.lineCount) {
                        val lineHeight = contentLayout.getLineBottom(i) - contentLayout.getLineTop(i)
                        if (currentY + lineHeight > pageHeight) {
                            document.finishPage(currentPage)
                            currentPage = document.startPage(pageInfo)
                            canvas = currentPage.canvas
                            currentY = padding.toFloat()
                        }
                        
                        val lineStart = contentLayout.getLineStart(i)
                        val lineEnd = contentLayout.getLineEnd(i)
                        val lineText = content.substring(lineStart, lineEnd)
                        val lineBaseline = currentY - contentLayout.getLineAscent(i)
                        
                        canvas.drawText(lineText, padding.toFloat(), lineBaseline, textPaint)
                        currentY += lineHeight
                    }
                    
                    document.finishPage(currentPage)
                    document.writeTo(out)
                    document.close()
                }
            }
        }
    }
}
