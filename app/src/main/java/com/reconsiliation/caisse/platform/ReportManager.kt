package com.reconsiliation.caisse.platform

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.reconsiliation.caisse.data.local.dao.VenteWithItems
import com.reconsiliation.caisse.data.local.entity.CustomerEntity
import com.reconsiliation.caisse.utils.FormatUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Génère un relevé de compte client au format PDF.
     * Isole PdfDocument et la logique graphique du repository.
     */
    fun generateCustomerStatementPdf(customer: CustomerEntity, ventes: List<VenteWithItems>): File? {
        val fileName = "Releve_${customer.name}_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)
        
        return try {
            val pdfDocument = PdfDocument()
            val titlePaint = Paint().apply {
                textSize = 18f
                isFakeBoldText = true
            }
            val textPaint = Paint().apply {
                textSize = 12f
            }
            val headerPaint = Paint().apply {
                textSize = 12f
                isFakeBoldText = true
            }

            var currentPageNumber = 1
            var y = 50f
            val margin = 50f
            val pageWidth = 595
            val pageHeight = 842

            fun createNewPage(): PdfDocument.Page {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber++).create()
                val page = pdfDocument.startPage(pageInfo)
                y = 50f
                return page
            }

            var page = createNewPage()
            var canvas = page.canvas

            canvas.drawText("RELEVÉ DE COMPTE CLIENT", 180f, y, titlePaint)
            y += 40f
            canvas.drawText("Client: ${customer.name}", margin, y, textPaint)
            y += 20f
            canvas.drawText("Téléphone: ${customer.phone}", margin, y, textPaint)
            y += 20f
            canvas.drawText("Dette Totale: ${FormatUtil.formatCurrency(customer.totalDebt)}", margin, y, textPaint)
            y += 40f

            canvas.drawText("Date", margin, y, headerPaint)
            canvas.drawText("Détail", 150f, y, headerPaint)
            canvas.drawText("Montant", 450f, y, headerPaint)
            y += 10f
            canvas.drawLine(margin, y, pageWidth - margin, y, textPaint)
            y += 25f

            ventes.forEach { v ->
                if (y > pageHeight - 50) {
                    pdfDocument.finishPage(page)
                    page = createNewPage()
                    canvas = page.canvas
                    canvas.drawText("Date", margin, y, headerPaint)
                    canvas.drawText("Détail", 150f, y, headerPaint)
                    canvas.drawText("Montant", 450f, y, headerPaint)
                    y += 30f
                }

                canvas.drawText(FormatUtil.formatShortDate(v.vente.date), margin, y, textPaint)
                val desc = if (v.vente.description.length > 35) v.vente.description.take(32) + "..." else v.vente.description
                canvas.drawText(desc, 150f, y, textPaint)
                canvas.drawText(FormatUtil.formatCurrency(v.vente.amount), 450f, y, textPaint)
                y += 25f
            }

            pdfDocument.finishPage(page)
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
